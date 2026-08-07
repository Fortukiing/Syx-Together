package coopmod;

final class CoopRemotePointer {

	int viewMode;
	int mouseScreenX;
	int mouseScreenY;
	int lookPixelX = -1;
	int lookPixelY = -1;
	int viewCenterX = -1;
	int viewCenterY = -1;
	int viewHalfWidth;
	int viewHalfHeight;
	int colorR = 127;
	int colorG = 127;
	int colorB = 127;
	boolean inGame = true;
	long sentMillis;
	long updatedMillis;
}
