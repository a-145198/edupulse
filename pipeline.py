import re
import subprocess
from pathlib import Path
from paddleocr import PaddleOCR


MODEL_PATH = Path("models/gemma-4-E2B-it-gpu.litertlm").resolve()
SAMPLES_DIR = Path("ocr/samples")

IMAGE_FILES = [
    #"kinematics_01.jpeg",
    "physics.jpeg",
    "chemistry01.jpeg",
    "chemistry02.jpeg",
    "maths01.jpeg",
    "maths02.jpeg",
]

TOKEN_CONFIDENCE_THRESHOLD = 0.85
IMAGE_REJECT_LOW_CONF_FRACTION = 0.5
IMAGE_REJECT_AVG_CONFIDENCE = 0.6

UNITS = {"N", "kg", "g", "mg", "m/s", "m/s²", "mol", "L", "cm", "mm", "km", "m", "s",
         "Hz", "Pa", "J", "W", "kJ", "kW", "kPa", "atm", "mL"}

# Characters that handwriting OCR commonly swaps with digits.
CHAR_TO_DIGIT = {
    "o": "0", "O": "0",
    "l": "1", "I": "1", "i": "1",
    "s": "5", "S": "5",
    "B": "8",
    "z": "2", "Z": "2",
    "b": "6",
    "q": "9",
}
DIGIT_TO_CHAR = {}  # reverse map: digit -> set of confused chars
for _ch, _dig in CHAR_TO_DIGIT.items():
    DIGIT_TO_CHAR.setdefault(_dig, set()).add(_ch)

# Pattern: a token glued to a unit suffix (e.g. "skg", "l5N", "Bkg", "i2N")
_UNIT_SUFFIXES_SORTED = sorted(UNITS, key=len, reverse=True)
_UNIT_SUFFIX_RE = re.compile(
    r'^(.+?)(' + '|'.join(re.escape(u) for u in _UNIT_SUFFIXES_SORTED) + r')$'
)


def _try_digit_recovery(prefix: str) -> str | None:
    """Try to convert a prefix into a number by applying the confusion matrix.

    Returns the recovered digit string if every character in prefix maps to a
    digit (directly or via the confusion table), otherwise None.
    """
    digits = []
    for ch in prefix:
        if ch.isdigit():
            digits.append(ch)
        elif ch in CHAR_TO_DIGIT:
            digits.append(CHAR_TO_DIGIT[ch])
        else:
            return None
    return "".join(digits) if digits else None


def recover_digits_near_units(text: str) -> tuple[str, list[dict]]:
    """Dynamically recover digit+unit tokens corrupted by OCR.

    Scans every whitespace-delimited token. When a token ends with a known
    unit suffix and the prefix looks like a corrupted number (all characters
    map to digits via the confusion matrix), it rewrites the token.

    Returns (cleaned_text, list_of_fixes_applied).
    """
    tokens = text.split()
    fixes = []
    out = []
    for tok in tokens:
        m = _UNIT_SUFFIX_RE.match(tok)
        if m:
            prefix, unit = m.group(1), m.group(2)
            recovered = _try_digit_recovery(prefix)
            if recovered and recovered != prefix:
                fixed = f"{recovered} {unit}"
                fixes.append({"original": tok, "fixed": fixed})
                out.append(fixed)
                continue
        out.append(tok)
    return " ".join(out), fixes


def fix_unit_ocr_errors(text: str) -> str:
    """Pattern-based cleanup for common OCR unit format corruptions."""
    # Standardize acceleration units
    text = re.sub(r'\bm[l1i]s2\b', 'm/s²', text, flags=re.IGNORECASE)
    text = re.sub(r'\bms[-_]?2\b', 'm/s²', text, flags=re.IGNORECASE)
    text = re.sub(r'\bm/s2\b', 'm/s²', text, flags=re.IGNORECASE)

    # Standardize velocity units
    text = re.sub(r'\bm[l1i]s\b', 'm/s', text, flags=re.IGNORECASE)
    text = re.sub(r'\bms[-_]?1\b', 'm/s', text, flags=re.IGNORECASE)
    return text


def join_multiline_ocr_artifacts(lines: list[str]) -> list[str]:
    """Rejoin lines split by OCR across line breaks."""
    if not lines:
        return lines

    joined = []
    i = 0
    while i < len(lines):
        current = lines[i].strip()
        if i + 1 < len(lines):
            next_line = lines[i + 1].strip()
            # Line ends with a trailing comma after digits — likely a split number
            if re.search(r'\d,\s*$', current) and next_line:
                joined.append(current.rstrip(', ') + next_line)
                i += 2
                continue
            # Line ends with a hyphenated word break
            if current.endswith('-') and re.match(r'^[a-zA-Z]+', next_line):
                joined.append(current[:-1] + next_line)
                i += 2
                continue

        joined.append(current)
        i += 1
    return joined


def get_ocr_engine() -> PaddleOCR:
    return PaddleOCR(
        use_doc_orientation_classify=True,
        use_doc_unwarping=False,
        use_textline_orientation=True,
        lang="en",
        text_det_limit_side_len=1536,
        text_det_limit_type="max",
        text_det_thresh=0.2,
        text_det_box_thresh=0.4,
    )


def extract_text_with_confidence(ocr: PaddleOCR, image_path: Path):
    results = ocr.predict(str(image_path))
    lines, scores = [], []
    for result in results:
        res_json = result.json["res"]
        lines.extend(res_json["rec_texts"])
        scores.extend(res_json.get("rec_scores", [None] * len(res_json["rec_texts"])))

    cleaned_lines = [fix_unit_ocr_errors(line) for line in lines]
    cleaned_lines = join_multiline_ocr_artifacts(cleaned_lines)
    return cleaned_lines, scores


def check_image_quality(lines: list[str], scores: list[float]) -> dict:
    valid_scores = [s for s in scores if s is not None]
    if not valid_scores:
        return {"reject": True, "reason": "No text detected in image."}

    avg_conf = sum(valid_scores) / len(valid_scores)
    low_frac = sum(1 for s in valid_scores if s < TOKEN_CONFIDENCE_THRESHOLD) / len(valid_scores)

    reject = (
        low_frac > IMAGE_REJECT_LOW_CONF_FRACTION
        or avg_conf < IMAGE_REJECT_AVG_CONFIDENCE
    )
    return {
        "reject": reject,
        "avg_confidence": avg_conf,
        "low_confidence_fraction": low_frac,
        "reason": (
            f"Image quality too low (avg conf {avg_conf:.2f}, "
            f"{low_frac:.0%} of lines below threshold). Please retake the photo "
            f"with better lighting and try again."
            if reject else None
        ),
    }


def ask_gemma(question_text: str) -> str:
    prompt = (
        "You are an offline AI tutor and homework solver.\n"
        "First, inspect the question text for potential OCR typos or missing numbers/units.\n"
        "If you spot suspicious OCR text, list it under 'OCR Warnings'. If none, write 'OCR Warnings: None'.\n\n"
        "Then solve the question accurately using this structure:\n"
        "OCR Warnings:\n"
        "Given:\n"
        "Formula:\n"
        "Substitution:\n"
        "Final Answer:\n\n"
        f"Question:\n{question_text}"
    )
    result = subprocess.run(
        ["uvx", "litert-lm", "run", str(MODEL_PATH),
         "--backend=gpu", f"--prompt={prompt}"],
        capture_output=True, text=True
    )
    return result.stdout


def process_image(ocr: PaddleOCR, image_path: Path):
    print(f"=== {image_path.name} ===", flush=True)
    lines, scores = extract_text_with_confidence(ocr, image_path)

    for line, score in zip(lines, scores):
        score_str = f"{score:.3f}" if score is not None else "N/A"
        print(f"  [{score_str}] {line}", flush=True)

    quality = check_image_quality(lines, scores)
    if quality["reject"]:
        print(f"\n🛑 REJECTED: {quality['reason']}\n", flush=True)
        print("=" * 60 + "\n", flush=True)
        return

    # Dynamic digit recovery pass
    all_fixes = []
    recovered_lines = []
    for line in lines:
        cleaned, fixes = recover_digits_near_units(line)
        recovered_lines.append(cleaned)
        all_fixes.extend(fixes)

    if all_fixes:
        print("\n🔧 Auto-corrected OCR digit/unit corruptions:", flush=True)
        for f in all_fixes:
            print(f"   '{f['original']}' → '{f['fixed']}'", flush=True)

    full_text = "\n".join(recovered_lines)
    print("\n--- Gemma response ---\n", flush=True)
    answer = ask_gemma(full_text)
    print(answer, flush=True)
    print("\n" + "=" * 60 + "\n", flush=True)


if __name__ == "__main__":
    ocr = get_ocr_engine()
    for filename in IMAGE_FILES:
        image_path = SAMPLES_DIR / filename
        if not image_path.exists():
            print(f"=== {filename} — FILE NOT FOUND, skipping ===\n", flush=True)
            continue
        process_image(ocr, image_path)