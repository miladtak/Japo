# Japo master backup

Latest source is on branch main.

Implemented baseline:
- video import/capture
- Media3 preview
- local project/timeline/layer persistence
- bundled person segmentation on extracted frames
- mask editing/refinement components
- chroma-key frame processing
- Media3 Transformer MP4 trim/effects export
- source-aware multi-clip timeline export backend
- local error log and backups

The current implementation status is maintained in docs/STATUS.md.
Advanced features remain explicitly marked incomplete until wired and tested.


### Latest source implementation pass — 2026-09-27
- Processed-video export now accepts a persistent background image bitmap in addition to timestamped background video frames.
- Background image decoding is bounded to a maximum dimension of 1920px to reduce memory pressure on mobile devices.
- Main export UI now exposes full-video person segmentation, chroma-key processing, manual-mask use, and background modes (none/color/blur/image/video).
- Chroma-key alpha is merged into the compositing mask so chroma removal and background replacement can be combined during full-video processing.
- Exporter ownership was corrected so a shared background image is not recycled after the first frame.
- No build, test, APK generation, or CI execution was performed; source implementation remains intentionally unvalidated until the coding pass is complete.
