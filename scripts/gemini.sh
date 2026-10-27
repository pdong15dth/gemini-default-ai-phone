#!/system/bin/sh
# "Hi Jovi" -> CopilotPanelWindow: dong panel bang Back (khong force-stop de giu service nghe).
# Nut loi tat -> ACTION_BUTTON_TO_START_FUNCTIONS: mo Gemini truc tiep.
# keyevent 231 (KEYCODE_VOICE_ASSIST) mo thang che do voice; 219 chi mo panel cho.
# sleep 0.5 de Tieu V nha han Micro (audio focus) truoc khi Gemini bat mic.
# keyevent 224 (KEYCODE_WAKEUP) bat man hinh (khong mo khoa) de Gemini hien duoc khi dang khoa may.
# Dấu \| để shell truyền regex "A|B" cho logcat mà không tách thành pipe.
while true; do
  logcat -b events -c
  logcat -b events -e CopilotPanelWindow\|ACTION_BUTTON_TO_START_FUNCTIONS -m 1 | grep -q CopilotPanelWindow && input keyevent 4
  sleep 0.5
  input keyevent 224
  sleep 0.5
  input keyevent 231
  sleep 1.5
done
