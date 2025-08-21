import cv2
import pytesseract
import numpy as np
import os

# Make sure tesseract is available (in Chaquopy, you may need to bundle Tesseract binaries or call via Android native code)
# pytesseract.pytesseract.tesseract_cmd = "/path/to/tesseract"  # Optional

def redact_image(image_path: str) -> str:
    """
    Detects text regions in the given image using Tesseract OCR and applies Gaussian blur.
    Saves the blurred image in the same directory with '_blurred' suffix.
    Returns the output image path.
    """

    # Define output path
    base, ext = os.path.splitext(image_path)
    output_path = f"{base}_blurred{ext}"

    # Load image
    image = cv2.imread(image_path)
    if image is None:
        raise ValueError(f"Could not load image: {image_path}")

    # Convert to grayscale for OCR
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

    # Run OCR to get bounding boxes
    boxes = pytesseract.image_to_data(gray, output_type=pytesseract.Output.DICT)

    n_boxes = len(boxes['level'])
    for i in range(n_boxes):
        conf = int(boxes['conf'][i])
        if conf > 30:  # confidence threshold
            x, y, w, h = boxes['left'][i], boxes['top'][i], boxes['width'][i], boxes['height'][i]
            roi = image[y:y+h, x:x+w]
            if roi.size > 0:
                blurred = cv2.GaussianBlur(roi, (31, 31), 30)
                image[y:y+h, x:x+w] = blurred

    # Save output
    cv2.imwrite(output_path, image)
    return output_path
