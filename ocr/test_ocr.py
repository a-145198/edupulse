import sys
from pathlib import Path

# Ensure root directory is in sys.path
sys.path.insert(0, str(Path(__file__).parent.parent.resolve()))

from pipeline import (
    fix_unit_ocr_errors,
    join_multiline_ocr_artifacts,
    recover_digits_near_units,
    _try_digit_recovery,
)


def test_fix_unit_ocr_errors():
    """Pattern-based unit format corrections (not hardcoded words)."""
    assert fix_unit_ocr_errors("acceleration is 5 mls2") == "acceleration is 5 m/s²"
    assert fix_unit_ocr_errors("velocity is 12 ms-1") == "velocity is 12 m/s"
    assert fix_unit_ocr_errors("speed 10 m/s2") == "speed 10 m/s²"
    # Should NOT touch normal words
    assert fix_unit_ocr_errors("the ball is moving") == "the ball is moving"


def test_try_digit_recovery():
    """Confusion matrix converts OCR-corrupted prefixes to digit strings."""
    assert _try_digit_recovery("s") == "5"
    assert _try_digit_recovery("l5") == "15"
    assert _try_digit_recovery("io") == "10"
    assert _try_digit_recovery("B") == "8"
    assert _try_digit_recovery("2o") == "20"
    assert _try_digit_recovery("12") == "12"  # already digits, no change
    # Non-recoverable prefixes return None
    assert _try_digit_recovery("abc") is None
    assert _try_digit_recovery("ma") is None


def test_recover_digits_near_units():
    """Dynamic digit+unit recovery works for arbitrary corrupted tokens."""
    # skg -> 5 kg (s=5, prefix ends with unit "kg")
    text, fixes = recover_digits_near_units("mass skg moving")
    assert "5 kg" in text
    assert len(fixes) == 1

    # l5N -> 15 N
    text, fixes = recover_digits_near_units("force of l5N")
    assert "15 N" in text

    # Bkg -> 8 kg
    text, fixes = recover_digits_near_units("weighs Bkg")
    assert "8 kg" in text

    # ioN -> 10 N (i=1, o=0)
    text, fixes = recover_digits_near_units("force ioN applied")
    assert "10 N" in text

    # Already correct tokens should not be changed
    text, fixes = recover_digits_near_units("mass 5 kg velocity 10 m/s")
    assert fixes == []
    assert text == "mass 5 kg velocity 10 m/s"


def test_join_multiline_ocr_artifacts():
    # Trailing comma after digit — line split
    lines = ["mass of 1,", "grams and volume"]
    joined = join_multiline_ocr_artifacts(lines)
    assert len(joined) == 1
    assert "1" in joined[0] and "grams" in joined[0]

    # Hyphenated word break
    hyphen_lines = ["Find the accel-", "eration of the car."]
    joined = join_multiline_ocr_artifacts(hyphen_lines)
    assert len(joined) == 1
    assert joined[0] == "Find the acceleration of the car."

    # Normal lines should not be merged
    normal = ["line one", "line two"]
    assert join_multiline_ocr_artifacts(normal) == ["line one", "line two"]


if __name__ == "__main__":
    test_fix_unit_ocr_errors()
    test_try_digit_recovery()
    test_recover_digits_near_units()
    test_join_multiline_ocr_artifacts()
    print("All OCR unit tests passed successfully!")
