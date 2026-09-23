# Japo — Offline Video Studio

Japo is an Android-first, local-only video editor.

## Current real functionality
- Import local videos and capture a video with the device camera app
- Media3 playback and seeking
- Local project, timeline clip and layer persistence
- Bundled ML Kit person/selfie segmentation on extracted frames
- Interactive alpha-mask brush component and alpha refinement
- Chroma key processing with selectable key color and spill suppression
- MP4 trim/export through Media3 Transformer
- Deterministic stylized filters: grayscale, invert, brightness, contrast and several named style presets
- Source-aware multi-clip timeline export backend
- Local error logging and text backups
- No network permission in the application manifest

## Important scope note
The project is being built phase-by-phase. Full-video matting, temporal segmentation integration, dedicated multi-person segmentation, background replacement over the whole video, advanced neural stylization, alpha-video export, and the complete timeline UI are not marked complete until they are actually wired and tested.

## Build
The repository contains a GitHub Actions workflow that builds a debug APK on pushes to main.
