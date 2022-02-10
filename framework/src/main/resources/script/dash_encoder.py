#!/usr/bin/env python3
"""
    This file is part of dash_encoder.
    dash_encoder is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    dash_encoder is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    You should have received a copy of the GNU General Public License
    along with dash_encoder. If not, see <http://www.gnu.org/licenses/>.
    Author: Steve Göring
"""
import argparse
import sys
import math
import os
import glob
import subprocess
import shutil

"""
based on:
    * https://www.crookm.com/journal/2018/transcoding-to-dash-and-hls-with-ffmpeg/
    * https://ffmpeg.org/ffmpeg-formats.html#dash-2
    * https://www.jungledisk.com/blog/2017/07/03/live-streaming-mpeg-dash-with-raspberry-pi-3/
    * https://rybakov.com/blog/mpeg-dash/
    * nice post about encoding: https://developers.google.com/media/vp9/settings/vod/
"""

def get_local_executable(path, executable):
    executable = list(glob.glob(os.path.join(path, executable)))
    if len(executable) == 0:
        return shutil.which(executable)  # use system's executable
    assert len(executable) == 1
    return executable[0]


def ffmpeg():
    return "ffmpeg"
    #return get_local_executable("ffmpeg-4*", "ffmpeg")


def ffprobe():
    return "ffprobe"
    #return get_local_executable("ffmpeg-4*", "ffprobe")


def shell_call(call):
    """
    Run a program via system call and return stdout + stderr.
    @param call programm and command line parameter list, e.g shell_call("ls /")
    @return stdout and stderr of programm call
    """
    try:
        output = subprocess.check_output(call, universal_newlines=True, shell=True)
    except Exception as e:
        output = str(e.output)
    return output


def get_fps(video):
    print("video: " + video)
    cmd = f"""{ffprobe()} -v 0 -of csv=p=0 -select_streams v:0 -show_entries stream=r_frame_rate {video}"""
    fps = shell_call(cmd).split("\n")[0]
    fps = fps.split("/")
    print("fps: " + str(fps))
    if len(fps) > 1:
        fps = int(math.ceil(int(fps[0].strip()) / int(fps[1].strip())))
        return fps
    return int(fps[0])


def build_video_encode_command(video, dashdir, height=240, seg_duration=2):
    # TODO: currently only CRF encoding possible
    # notes for other codecs
    #    -an -c:v libaom-av1 -row-mt 1
    #    -crf 30 -b:v 0 -strict experimental
    #    -cpu-used 4
    #    -preset slow
    #    -an -c:v libx265
    #    -crf 28
    #-x265-params "keyint=25:min-keyint=25"
    #-tune film -x264opts 'keyint=25:min-keyint=25:no-scenecut'
    # -x264opts 'keyint=25:min-keyint=25:no-scenecut'
    encoding = {
        240: {"crf": 32, "preset": "medium"},
        360: {"crf": 32, "preset": "medium"},  # compensate lower resolution with better preset
        576: {"crf": 28, "preset": "medium"},
        720: {"crf": 24, "preset": "fast"},
        1080: {"crf": 24, "preset": "fast"}
    }

    dashdir = str(dashdir)
    print("dash: " + dashdir)

    tempVideoDir = os.path.splitext(os.path.basename(video))[0] + f"_{height}p.mp4"
    print("tempVideoDir: " + tempVideoDir)

    output = os.path.join(dashdir, tempVideoDir)
    fps = get_fps(video)
    crf = 24
    preset = "slow"
    if height in encoding:
        crf = encoding[height]["crf"]
        preset = encoding[height]["preset"]

    cmd = f"""
        {ffmpeg()} -y -hide_banner
        -i "{video}"
        -preset {preset}
        -an -c:v libx264
        -crf {crf}
        -x264opts 'keyint={seg_duration*fps}:min-keyint={seg_duration*fps}:no-scenecut'
        -g {seg_duration}
        -pix_fmt yuv420p
        -vf "scale=-2:{height}"
        -f mp4
        "{output}" """
    cmd = " ".join(cmd.split())
    return cmd, output


def build_audio_encode_command(video, dashdir):
    output = os.path.join(dashdir, os.path.splitext(os.path.basename(video))[0] + f"_audio.m4a")
    cmd = f"""
        {ffmpeg()} -y -hide_banner
        -i {video}
        -c:a aac
        -ac 2
        -b:a 192k
        -vn
         {output}
        """
    cmd = " ".join(cmd.split())
    return cmd, output


def build_manifest_command(video, video_files, audio_files, dashdir, seg_duration=2):
    manifest_part = " ".join(
        [f"-i {i}" for i in video_files + audio_files]
    )

    map_part = " ".join(
        [f"-map {i}:v" for i in range(len(video_files))] +
        [f"-map {i}:a" for i in range(len(video_files) + 1, len(video_files + audio_files))]
    )

    output = os.path.join(dashdir, os.path.splitext(os.path.basename(video))[0] + f".mpd")

    file_name = os.path.basename(video)
    file_name = file_name.split(".")[0]
    print("file_name: " + file_name)

    cmd = f"""
        {ffmpeg()} -y
        {manifest_part}
        -c copy
        {map_part}
        -f dash
        -use_template 1 -use_timeline 0
        -seg_duration {seg_duration}
        -adaptation_sets "id=0,streams=v id=1,streams=a"
        -init_seg_name "{file_name}_init\$RepresentationID\$.m4s"
        -media_seg_name "{file_name}_chunk\$RepresentationID\$-\$Number%05d\$.m4s"
        {output}
        """

    cmd = " ".join(cmd.split())
    print(cmd)
    return cmd, output


def build_thumbnail(video, dashdir):
    output = os.path.join(dashdir, os.path.splitext(os.path.basename(video))[0] + f"_thumb.png")
    cmd = f"""
        {ffmpeg()} -y -hide_banner
        -i {video}
        -ss 00:00:02
        -vf "scale=-2:540"
        -vframes 1
         {output}
        """
    cmd = " ".join(cmd.split())
    return cmd, output


def main(_):
    # argument parsing
    parser = argparse.ArgumentParser(description='create dash representations',
                                     epilog="stg7 2019",
                                     formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument("--video", type=str, help="video to convert to a dash version")
    parser.add_argument("--dash_folder", type=str, default="dash", help="folder for storing the dash video")
    parser.add_argument("--auto_subfolders", "-as", action="store_true", help="create subfolder based on videoname")
    parser.add_argument("--no_encoding", "-ne", action="store_true", help="don't encode the videos again")

    a = vars(parser.parse_args())

    print(f"used cli parmeters: {a}")

    if a["auto_subfolders"]:
        subfolder = os.path.splitext(os.path.basename(a["video"]))[0]
        a["dash_folder"] = os.path.join(a["dash_folder"], subfolder)
    os.makedirs(a["dash_folder"], exist_ok=True)

    print(f"store dashed video in {a['dash_folder']}")

    # TODO: filter out higher resolutions based on input video?!
    resolutions = [360, 720] #, 240, 576, 540, 720, 1080] #, 1440, 2160]  # TODO: extend, check, update

    seg_duration = 2
    # collect all commands and output files
    commands = []
    video_files = []
    for resolution in resolutions:
        cmd, outfile = build_video_encode_command(a["video"], a["dash_folder"], resolution, seg_duration=seg_duration)
        commands.append(cmd)
        video_files.append(outfile)

    # for audio only one quality is considered
    # TODO: maybe use more?
    cmd, audio_file = build_audio_encode_command(a["video"], a["dash_folder"])
    commands.append(cmd)

    # print("\n".join(commands))
    # print("\n".join(video_files))
    # print(audio_file)
    if not a["no_encoding"]:
        for x in commands:
            print(f"run {x}")
            os.system(x)

    print("create thumbnail")
    cmd, outfile = build_thumbnail(a["video"], a["dash_folder"])
    if not os.path.isfile(outfile):
        os.system(cmd)
        print(f"thumbnail created {outfile}")
    else:
        print(f"thumbnail reused")
        print(cmd)

    # print("create manifest file")
    cmd, outfile = build_manifest_command(a["video"], video_files, [audio_file], a["dash_folder"], seg_duration=seg_duration)
    # print(cmd)
    #return
    os.system(cmd)

    print(f"done :-) use {outfile} in your player")


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
