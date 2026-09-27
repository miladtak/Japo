# Japo Phases

001 Core + Import Video + Preview
002 Human Detection + Segmentation
003 Human Matting + Edge Refinement
004 Chroma Key + Spill Removal
005 Multi-Person Tracking
006 Background Removal + Replacement
007 Anime + Pencil + Ink + Watercolor
008 Temporal Consistency
009 Timeline + Layers + Editing
010 Export + Project Save
011 Backup + Error Log
012 Optimization + Final Testing


### Latest source implementation pass — 2026-09-27
- Processed-video export now accepts a persistent background image bitmap in addition to timestamped background video frames.
- Background image decoding is bounded to a maximum dimension of 1920px to reduce memory pressure on mobile devices.
- Main export UI now exposes full-video person segmentation, chroma-key processing, manual-mask use, and background modes (none/color/blur/image/video).
- Chroma-key alpha is merged into the compositing mask so chroma removal and background replacement can be combined during full-video processing.
- Exporter ownership was corrected so a shared background image is not recycled after the first frame.
- No build, test, APK generation, or CI execution was performed; source implementation remains intentionally unvalidated until the coding pass is complete.
