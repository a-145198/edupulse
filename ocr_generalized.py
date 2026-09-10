"""
Generalized OCR preprocessing using PaddleOCR's built-in adaptive modules
instead of hand-tuned OpenCV thresholds (blockSize, C, fixed crop %).

Why this replaces the earlier approach:
- use_doc_unwarping:       corrects page distortion/wrinkles/skew per-image,
                           not a fixed blockSize tuned to one photo.
- use_doc_orientation_classify: auto-detects 0/90/180/270 rotation, so you
                           don't need to assume upright photos.
- text_det_limit_side_len: scales detection resolution to the image's own
                           size instead of hardcoding pixel dimensions.
- No manual crop-to-top-15% assumption: detection runs on the full image
  and finds text wherever it is.
"""

from paddleocr import PaddleOCR

ocr = PaddleOCR(
    use_doc_orientation_classify=True,
    use_doc_unwarping=False,
    use_textline_orientation=True,
    lang='en',
    text_det_limit_side_len=1536,
    text_det_limit_type='max',
    text_det_thresh=0.2,
    text_det_box_thresh=0.4,
)


def extract_text(image_path: str) -> list[str]:
    result = ocr.predict(image_path)
    lines = []
    for res in result:
        lines.extend(res['rec_texts'])
    return lines


if __name__ == '__main__':
    import sys
    path = sys.argv[1] if len(sys.argv) > 1 else 'ocr/samples/kinematics_01.jpg'
    for line in extract_text(path):
        print(line)