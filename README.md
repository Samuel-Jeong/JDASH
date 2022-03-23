# MPEG DASH Program
~~~
비디오(+오디오) 라이브 스트리밍 서버 & 클라이언트

1. 상기 프로그램은, 
    1) DASH 서버로서 다른 DASH 클라이언트로부터 요청을 받아 처리할 수 있고, 
    2) DASH 클라이언트로서 다른 DASH 서버에 요청을 보내서 미디어 스트림을 가져올 수 있다.
    3) 서버와 클라이언트 기능은 서로 구분되어 동작하지 않고, 상호 운용된다.

2. DASH 서버로 동작할 때, 
    1) 미디어 스트림을 가져올 때 현재는 [RTMP, DASH] 방식을 사용 가능
    2) DASH 방식을 사용하여 static media stream 을 미리 가져오기 가능 (DASH 클라이언트로 동작)
        > dynamic media stream 은 rtmp 서버에 publish 했다는 가정에 하에, 
          published media stream 을 사용자가 요청하기 전에 미리 가져오도록 구현되어있음
          (RTMP 클라이언트로 동작)

3. DASH 클라이언트로 동작할 때, 
    1) 미디어 스트림 송출할 때는 (publishing), 비디오와 오디오 모두 송출 필요 > 사용하는 Open API 에서 오디오만 송출되지 않음
        > RTMP 를 사용하면 비디오 + 오디오 필수, DASH 사용하면 비디오 또는 오디오 선택 가능
    2) 미디어 스트림 수신할 때는 (playing), 비디오 또는 오디오 수신 > 오디오만 따로 수신 가능

4 REFERENCE
  1) Make MPD & Segments
    + REF : org.bytedeco.javacv (static, dynamic)
            (https://github.com/bytedeco/javacv)

  2) MPD class object & Validatation of MPD file
    + REF : carlanton/mpd-tools (io.linstrom:mpd-parser)
            (https://github.com/carlanton/mpd-tools)

  3) Camera
    + REF : org.bytedeco.javacv
            (https://github.com/bytedeco/javacv)

~~~

## Service
![dash_stream_flow](https://user-images.githubusercontent.com/37236920/159614833-43d3128c-fadb-435f-a528-80d496463b57.png)
  
![스크린샷 2022-02-15 오후 4 02 51](https://user-images.githubusercontent.com/37236920/154009715-e31fbbd9-d4b9-489d-93ed-ec72d3c00b1a.png)
  
![스크린샷 2022-02-10 오전 9 40 31](https://user-images.githubusercontent.com/37236920/153314792-6cc61897-911f-4924-a8fc-79ce2cf6131a.png)
  
![스크린샷 2022-02-15 오후 4 00 09](https://user-images.githubusercontent.com/37236920/154009420-a567b62f-65b4-41a2-8f8c-a64962c628d9.png)
  
### (1) DRM 적용 전
![스크린샷 2022-02-09 오후 3 47 00](https://user-images.githubusercontent.com/37236920/153136606-7c5bbc7c-249f-4b8d-a3ea-3b73cc8277ae.png)
  
### (2) DRM 적용 후
![스크린샷 2022-02-09 오후 3 47 20](https://user-images.githubusercontent.com/37236920/153136655-ae0c1257-ba93-4c56-b355-5c22eae7b844.png)
  
## Flow
### (1) DRM 적용 전
![스크린샷 2022-02-09 오후 3 44 54](https://user-images.githubusercontent.com/37236920/153136334-78c4ca9a-ef10-42f1-bcea-40a263869f1c.png)
  
### (2) DRM 적용 후
![스크린샷 2022-02-09 오후 3 45 54](https://user-images.githubusercontent.com/37236920/153136472-932d3a75-a20f-452f-b31e-6d7a2e9b2929.png)
  
## Data structure
![스크린샷 2022-02-04 오전 9 31 14](https://user-images.githubusercontent.com/37236920/152452171-363bed03-416d-433a-85d5-b85c394b1ff4.png)
  
