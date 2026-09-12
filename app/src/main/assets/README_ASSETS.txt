This folder needs one file that I could not download myself (no internet
access in the environment I built this app in):

    hand_landmarker.task

Get it from Google's official model page (free, no account needed):
https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task

Download it and drop it directly in this "assets" folder (same folder as this
text file). Without it, barcode/product scanning still works fully — only the
fingertip-trail "Iron Man" effect is skipped, and the app will show a small
status message saying so instead of crashing.
