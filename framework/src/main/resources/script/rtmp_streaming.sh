#!/bin/sh

# ARGUMENTS
SEGMENT_NAME=$1
VIDEO_IN=$2
VIDEO_OUT=$3

# VIDEO OPTIONS
VIDEO_CODEC=libx265
FPS=30
GOP_SIZE=30
V_SIZE_1=960x540
V_SIZE_2=416x234
V_SIZE_3=640x360
V_SIZE_4=768x432
V_SIZE_5=1280x720
V_SIZE_6=1920x1080

# AUDIO OPTIONS
AUDIO_CODEC=aac
AUDIO_SAMPLING_RATE=44100
AUDIO_CHANNEL=1
AUDIO_BIT_RATE=128k
PRESET_P=veryfast

# MPD
SEG_DURATION=2

# FFMPEG
ffmpeg -i ${VIDEO_IN} \
    -preset ${PRESET_P} -keyint_min ${GOP_SIZE} -g ${GOP_SIZE} \
    -r ${FPS} -c:v ${VIDEO_CODEC} -c:a ${AUDIO_CODEC} -b:a ${AUDIO_BIT_RATE} -ac ${AUDIO_CHANNEL} -ar ${AUDIO_SAMPLING_RATE} \
    -map v:0 -s:2 $V_SIZE_3 -b:v:2 2M -maxrate:2 3M -bufsize:2 4M \
    -map 0:a \
    -init_seg_name ${SEGMENT_NAME}_init\$RepresentationID\$.\$ext\$ -media_seg_name ${SEGMENT_NAME}_chunk\$RepresentationID\$-\$Number%05d\$.\$ext\$ \
    -use_template 1 -use_timeline 1  \
    -seg_duration ${SEG_DURATION} -adaptation_sets "id=0,streams=v id=1,streams=a" \
    -f dash ${VIDEO_OUT}



    #-threads 16 \
    # -remove_at_exit 1 \
    #-sc_threshold 0
    #-pix_fmt yuv420p

     #-map v:0 -s:0 $V_SIZE_1 -b:v:0 2M -maxrate:0 2.14M -bufsize:0 3.5M \
        #-map v:0 -s:1 $V_SIZE_2 -b:v:1 145k -maxrate:1 155k -bufsize:1 220k \
        #-map v:0 -s:3 $V_SIZE_4 -b:v:3 730k -maxrate:3 781k -bufsize:3 1278k \
        #-map v:0 -s:4 $V_SIZE_4 -b:v:4 1.1M -maxrate:4 1.17M -bufsize:4 2M \
        #-map v:0 -s:5 $V_SIZE_5 -b:v:5 3M -maxrate:5 3.21M -bufsize:5 5.5M \
        #-map v:0 -s:6 $V_SIZE_5 -b:v:6 4.5M -maxrate:6 4.8M -bufsize:6 8M \
        #-map v:0 -s:7 $V_SIZE_6 -b:v:7 6M -maxrate:7 6.42M -bufsize:7 11M \
        #-map v:0 -s:8 $V_SIZE_6 -b:v:8 7.8M -maxrate:8 8.3M -bufsize:8 14M \