#!/system/bin/sh
# "Hi Jovi" -> CopilotPanelWindow: dong panel bang Back (khong force-stop de giu service nghe).
# Nut loi tat -> ACTION_BUTTON_TO_START_FUNCTIONS: mo Gemini truc tiep.
# Dấu \| để shell truyền regex "A|B" cho logcat mà không tách thành pipe.
while true; do
  logcat -b events -c
  logcat -b events -e CopilotPanelWindow\|ACTION_BUTTON_TO_START_FUNCTIONS -m 1 | grep -q CopilotPanelWindow && input keyevent 4
  sleep 0.3
  input keyevent 219
  sleep 1.5
done
