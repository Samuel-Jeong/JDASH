#! /usr/bin/python3

import argparse
import sys
import math
import os
import glob
import subprocess
import shutil
import ffmpeg_streaming
from ffmpeg_streaming import Formats, Bitrate, Representation, Size

def shell_call(call):
    try:
        output = subprocess.check_output(call, universal_newlines=True, shell=True)
        print(output)
    except Exception as e:
        output = str(e.output)
    return output


cmd = f"""ffmpeg -y -hide_banner -f avfoundation -list_devices true -i \"\""""
cmd = " ".join(cmd.split())
cam_name = shell_call(cmd).split("\n")[0]
cam_name = cam_name.split("/")
print("cam_name: " + str(cam_name))

#_144p  = Representation(Size(256, 144), Bitrate(95 * 1024, 64 * 1024))
#_240p  = Representation(Size(426, 240), Bitrate(150 * 1024, 94 * 1024))
#_360p  = Representation(Size(640, 360), Bitrate(276 * 1024, 128 * 1024))
#_480p  = Representation(Size(854, 480), Bitrate(750 * 1024, 192 * 1024))
_480p  = Representation(Size(640, 480), Bitrate(750 * 1024, 192 * 1024))
#_720p  = Representation(Size(1280, 720), Bitrate(2048 * 1024, 320 * 1024))
#_720p  = Representation(Size(1280, 720), Bitrate(2048 * 1024, 320 * 1024))
#_1080p = Representation(Size(1920, 1080), Bitrate(4096 * 1024, 320 * 1024))
#_2k    = Representation(Size(2560, 1440), Bitrate(6144 * 1024, 320 * 1024))
#_4k    = Representation(Size(3840, 2160), Bitrate(17408 * 1024, 320 * 1024))

video = ffmpeg_streaming.input("0:0", capture=True)
dash = video.dash(Formats.h264())
dash.representations(_480p)
dash.output("./dash.mpd")
