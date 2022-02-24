#!/bin/sh

# ARGUMENTS
VIDEO_IN=$1
VIDEO_OUT=$2

# FFMPEG
ffmpeg -re -i ${VIDEO_IN} \
    -c copy \
    -f mp4 ${VIDEO_OUT}
