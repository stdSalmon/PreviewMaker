package com.tistory.deque.previewmaker;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class PreviewCanvasView extends View {
  private final static String TAG = "PreviewEditActivity";

  public static final int CANVAS_WIDTH_MAX_SIZE = 3000;
  public static final int CANVAS_HEIGHT_MAX_SIZE = 3000;

  private static ClickState CLICK_STATE;

  private Canvas mCanvas;
  private PreviewEditActivity mActivity;
  private int canvasWidth, canvasHeight;
  public static int grandParentWidth, grandParentHeight;

  private ArrayList<PreviewItem> previewItems;
  private int previewPosWidth, previewPosHeight;
  private int previewWidth, previewHeight;

  private boolean isStampShown = false;
  private StampItem stampItem;
  private int stampWidth, stampHeight, stampPosWidthPer, stampPosHeightPer;
  private Uri stampURI;
  private Bitmap stampOriginalBitmap;
  private int stampWidthPos, stampHeightPos;
  private double stampRate;

  private float stampGuideRectWidth = 5f;
  private float stampGuideLineWidth = 2f;
  private float stampGuideCircleRadius = 15f;

  private int movePrevX, movePrevY;

  private ProgressDialog saveProgressDialog;


  public PreviewCanvasView(Context context, PreviewEditActivity activity, ArrayList<PreviewItem> previewItems) {
    super(context);
    this.mActivity = activity;
    this.previewItems = previewItems;
    CLICK_STATE = ClickState.getClickState();
    CLICK_STATE.start();

    saveProgressDialog = new ProgressDialog(mActivity);
    saveProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    saveProgressDialog.setCancelable(false);
    saveProgressDialog.setCanceledOnTouchOutside(false);
    saveProgressDialog.setMessage("저장중입니다..");
  }
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    mCanvas = canvas;
    setBackgroundColor(ContextCompat.getColor(getContext(), R.color.backgroundGray));
    if (PreviewEditActivity.POSITION < 0) { // 프리뷰들중에서 아무런 프리뷰도 선택하지 않았을 때
      setBackgroundColor(Color.WHITE);
    } else {
      drawBellowBitmap();
      if (isStampShown){
        Logger.d(TAG, "stamp shown true");
        drawStamp();
        if(CLICK_STATE.getClickStateEnum() == ClickStateEnum.STATE_STAMP_EDIT ||
           CLICK_STATE.getClickStateEnum() == ClickStateEnum.STATE_STAMP_ZOOM ){
          drawStampEditGuide();
        }
      }
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    this.setMeasuredDimension(CANVAS_WIDTH_MAX_SIZE, CANVAS_HEIGHT_MAX_SIZE);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    switch (event.getAction()){
      case MotionEvent.ACTION_DOWN :
        touchDown(event);
        break;
      case MotionEvent.ACTION_MOVE :
        touchMove(event);
        break;
      case MotionEvent.ACTION_UP :
        touchUp(event);
        break;
    }
    return true;
  }

  private void touchDown(MotionEvent event){
    int x, y;
    x = (int) event.getX();
    y = (int) event.getY();

    movePrevX = x;
    movePrevY = y;

    if(isTouchInStamp(x,y)){
      CLICK_STATE.clickStamp();
    }
    if(isTouchStampZoom(x,y)){
      CLICK_STATE.clickStampZoomStart();
    }
    mActivity.editButtonGoneOrVisible(CLICK_STATE);

    invalidate();
  }


  private void touchMove(MotionEvent event){
    int x, y;
    x = (int) event.getX();
    y = (int) event.getY();

    switch (CLICK_STATE.getClickStateEnum()){
      case STATE_NONE_CLICK:
        break;

      case STATE_STAMP_EDIT:
        int deltaX = x - movePrevX;
        int deltaY = y - movePrevY;
        stampWidthPos += deltaX;
        stampHeightPos += deltaY;

        invalidate();
        break;

      case STATE_STAMP_ZOOM:
        double nowDist;
        double stampCenterX, stampCenterY;
        double newHeight, newWidth;

        stampCenterX = stampWidthPos + stampWidth / 2.0f;
        stampCenterY = stampHeightPos + stampHeight / 2.0f;

        nowDist = Math.sqrt(Math.pow(stampCenterX - x, 2) + Math.pow(stampCenterY - y, 2));

        newHeight = (2.0f * nowDist) / Math.sqrt( (Math.pow(stampRate, 2) + 1 ) );
        newWidth = newHeight * stampRate;

        stampWidthPos = (int) (stampCenterX - newWidth / 2);
        stampHeightPos = (int) (stampCenterY - newHeight / 2);
        stampWidth = (int) (stampCenterX + newWidth / 2) - stampWidthPos + 1;
        stampHeight = (int) (stampCenterY + newHeight / 2) - stampHeightPos + 1;

        invalidate();
        break;
    }

    movePrevX = x;
    movePrevY = y;

  }
  private void touchUp(MotionEvent event){
    CLICK_STATE.clickStampZoomEnd();
  }

  private boolean isInBox(int x, int y, int x1, int y1, int x2, int y2){
    if(x > x1 && x < x2 && y > y1 && y < y2) return true;
    else return false;
  }

  private boolean isInBoxWithWidth(int x, int y, int x1, int y1, int xWidth, int yWidth){
    if(x > x1 && x < x1 + xWidth && y > y1 && y < y1 + yWidth) return true;
    else return false;
  }

  private boolean isInBoxWithRadius(int x, int y, int xCenter, int yCenter, int radius){
    if( (x-xCenter) * (x-xCenter) + (y-yCenter) * (y-yCenter) < radius * radius) return true;
    else return false;
  }

  private boolean isTouchInStamp(int x, int y){
    return isInBoxWithWidth(x, y, stampWidthPos, stampHeightPos, stampWidth, stampHeight);
  }

  private boolean isTouchStampZoom(int x, int y){
    int radius = (int) (stampGuideCircleRadius + 15);
    int x_s = stampWidthPos; //x start
    int x_e = stampWidthPos + stampWidth; //x end
    int y_s = stampHeightPos; // y start
    int y_e = stampHeightPos + stampHeight; // y end

    if (isInBoxWithRadius(x, y, x_s, y_s, radius)
      || isInBoxWithRadius(x, y, x_s, y_e, radius)
      || isInBoxWithRadius(x, y, x_e, y_s, radius)
      || isInBoxWithRadius(x, y, x_e, y_e, radius)) {
      return true;
    } else {
      return false;
    }
  }

  private void drawBellowBitmap() {
    Bitmap previewBitmap = previewItems.get(PreviewEditActivity.POSITION).getmBitmap();
    canvasWidth = grandParentWidth - 16;
    canvasHeight = grandParentHeight - 16; // layout margin
    int previewBitmapWidth = previewBitmap.getWidth();
    int previewBitmapHeight = previewBitmap.getHeight();
    Logger.d(TAG, "CANVAS : W : " + canvasWidth + " , H : " + canvasHeight);

    double rate = (double) previewBitmapWidth / (double) previewBitmapHeight;
    Rect dst;
    double canvasRate = (double) canvasWidth / (double) canvasHeight;

    if(rate >= canvasRate && previewBitmapWidth >= canvasWidth) { // w > h

      previewPosWidth = 0;
      previewPosHeight = (canvasHeight - (int) (canvasWidth * (1 / rate))) / 2;
      previewWidth = canvasWidth;
      previewHeight = (int) (canvasWidth * (1 / rate));

    } else if (rate < canvasRate && previewBitmapHeight >= canvasHeight) { // w < h

      previewPosWidth = (canvasWidth -(int) (canvasHeight * (rate))) / 2;
      previewPosHeight = 0;
      previewWidth = (int) (canvasHeight * (rate));
      previewHeight = canvasHeight;

    } else {

      previewPosWidth = (canvasWidth - previewBitmapWidth) / 2;
      previewPosHeight = (canvasHeight - previewBitmapHeight) / 2;
      previewWidth = previewBitmapWidth;
      previewHeight = previewBitmapHeight;

    }
    dst = new Rect(previewPosWidth, previewPosHeight, previewPosWidth + previewWidth, previewPosHeight + previewHeight);
    Logger.d(TAG, previewPosWidth +","+ previewPosHeight +","+ (previewPosWidth + previewWidth)+","+ (previewPosHeight + previewHeight));
    mCanvas.drawBitmap(previewBitmap, null, dst, null);
  }

  private void drawStamp(){
    if(stampItem == null) {
      Logger.d(TAG, "no stamp item");
      return;
    }
    Logger.d(TAG, "STAMP draw : w, h, pw, ph, uri : " +
      stampWidth + ", " + stampHeight + ", " +
      stampPosWidthPer + ", " + stampPosHeightPer + ", " + stampURI);

    Rect dst =  new Rect(stampWidthPos, stampHeightPos, stampWidthPos + stampWidth, stampHeightPos + stampHeight);
    mCanvas.drawBitmap(stampOriginalBitmap, null, dst,null);
  }

  private void drawStampEditGuide() {
    int x_s = stampWidthPos; //x start
    int x_e = stampWidthPos + stampWidth; //x end
    int x_l = stampWidth; //x length
    int y_s = stampHeightPos; // y start
    int y_e = stampHeightPos + stampHeight; // y end
    int y_l = stampHeight; // y length

    Paint stampRect = new Paint();
    stampRect.setStrokeWidth(stampGuideRectWidth);
    stampRect.setColor(Color.WHITE);
    stampRect.setStyle(Paint.Style.STROKE);
    mCanvas.drawRect(x_s, y_s, x_e, y_e, stampRect);


    Paint stampGuideLine = new Paint();
    stampGuideLine.setStrokeWidth(stampGuideLineWidth);
    stampGuideLine.setColor(Color.WHITE);
    mCanvas.drawLine(x_s, y_l / 3.0f + y_s, x_e, y_l / 3.0f + y_s, stampGuideLine);
    mCanvas.drawLine(x_s, y_l * 2 / 3.0f + y_s, x_e, y_l * 2 / 3.0f + y_s, stampGuideLine);
    mCanvas.drawLine(x_l / 3.0f + x_s, y_s, x_l / 3.0f + x_s, y_e, stampGuideLine);
    mCanvas.drawLine(x_l * 2 / 3.0f + x_s, y_s, x_l * 2 / 3.0f + x_s, y_e, stampGuideLine);

    Paint stampGuideCircle = new Paint();
    stampGuideCircle.setColor(Color.WHITE);
    mCanvas.drawCircle(x_s, y_s, stampGuideCircleRadius, stampGuideCircle);
    mCanvas.drawCircle(x_s, y_e, stampGuideCircleRadius, stampGuideCircle);
    mCanvas.drawCircle(x_e, y_s, stampGuideCircleRadius, stampGuideCircle);
    mCanvas.drawCircle(x_e, y_e, stampGuideCircleRadius, stampGuideCircle);
  }

  public void finishStampEdit(){
    CLICK_STATE.clickFinishStampEdit();
    mActivity.editButtonGoneOrVisible(CLICK_STATE);

    int id = stampItem.getID();

    stampPosWidthPer = (int) (((stampWidth / 2.0f) + stampWidthPos - previewPosWidth ) * 100000.0f / (previewWidth) );
    stampPosHeightPer = (int) (((stampHeight / 2.0f) + stampHeightPos - previewPosHeight ) * 100000.0f / (previewHeight) );

    stampItem.setWidth(stampWidth);
    stampItem.setHeight(stampHeight);

    stampItem.setPos_width_per(stampPosWidthPer);
    stampItem.setPos_height_per(stampPosHeightPer);

    mActivity.stampUpdate(id, stampWidth, stampHeight, stampPosWidthPer, stampPosHeightPer);

    invalidate();
  }

  protected void showStamp(){
    setStampShown(true);
    CLICK_STATE.clickStampButton();
    previewItems.get(PreviewEditActivity.POSITION).editted();
    mActivity.editButtonGoneOrVisible(CLICK_STATE);
  }

  protected void callInvalidate(){
    invalidate();
  }

  protected boolean isStampShown() {
    return isStampShown;
  }

  protected void setStampShown(boolean stampShown) {
    isStampShown = stampShown;
  }

  public StampItem getStampItem() {
    return stampItem;
  }

  public void setStampItem(StampItem stampItem) {
    this.stampItem = stampItem;
    stampPosWidthPer = stampItem.getPos_width_per();
    stampPosHeightPer = stampItem.getPos_height_per();
    stampURI = stampItem.getImageURI();
    stampOriginalBitmap = stampURIToBitmap(stampURI, mActivity);

    stampWidth = stampItem.getWidth();
    stampHeight = stampItem.getHeight();
    if(stampWidth < 0 || stampHeight < 0){
      int id = stampItem.getID();
      stampWidth = stampOriginalBitmap.getWidth();
      stampHeight = stampOriginalBitmap.getHeight();
    }

    stampRate = (double) stampWidth / (double) stampHeight;

    stampWidthPos = (int) ((stampPosWidthPer * previewWidth / 100000.0f) - (stampWidth / 2.0f) + previewPosWidth);
    stampHeightPos = (int) ((stampPosHeightPer * previewHeight / 100000.0f) - (stampHeight / 2.0f) + previewPosHeight);

    Logger.d(TAG, "STAMP set : w, h, pw, ph, uri : " +
      stampWidth + ", " + stampHeight + ", " +
      stampPosWidthPer + ", " + stampPosHeightPer + ", " + stampURI);
  }

  public Bitmap stampURIToBitmap(Uri imageUri, Activity activity){
    try{
      Bitmap bitmap = MediaStore.Images.Media.getBitmap(activity.getContentResolver(), imageUri);
      return bitmap;
    } catch (IOException e) {
      Logger.d(TAG, "URI -> Bitmap : IOException" + imageUri);
      e.printStackTrace();
      return null;
    }
  }

  public void saveEnd(){
    try {
      saveProgressDialog.dismiss();
    } catch (Exception e) {}
  }

  public void savePreviewEach(int nextPosition){
    /**
     * if nextPosition == -1 , there is only click 'save',
     * or not, there is only click 'YES' in save question dialog.
     * 만약 nextPosition이 -1이면, 저장 버튼을 눌린것임.
     * 그렇지 않으면, "저장하시겠습니까" 에서 YES를 눌린 것임.
     */

    saveProgressDialog.show();
    SaveAllAsyncTask saveAllAsyncTask = new SaveAllAsyncTask();
    saveAllAsyncTask.execute(nextPosition);
  }

  public boolean isNowEditingStamp() {
    if(CLICK_STATE.getClickStateEnum() == ClickStateEnum.STATE_NONE_CLICK) return false;
    else return true;
  }

  public void clickNewPreview(final int nextPosition) {
    AlertDialog.Builder stampDeleteAlert = new AlertDialog.Builder(mActivity);
    stampDeleteAlert.setMessage(mActivity.getString(R.string.snackbar_preview_edit_acti_clk_new_preview)).setCancelable(true)
      .setPositiveButton("YES",
        new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          savePreviewEach(nextPosition);
          //changeAndInitPreviewInCanvas(nextPosition);
          return;
        }
      })
      .setNegativeButton("NO",
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            changeAndInitPreviewInCanvas(nextPosition);
            return;
          }
        })
      .setNeutralButton("CANCLE",
      new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          return;
        }
      });


    if(PreviewEditActivity.POSITION != -1){
      if(!previewItems.get(PreviewEditActivity.POSITION).getIsSaved()) {
        AlertDialog alert = stampDeleteAlert.create();
        alert.setCanceledOnTouchOutside(false);
        alert.show();
      } else {
        changeAndInitPreviewInCanvas(nextPosition);
      }
    }
  }

  public void changeAndInitPreviewInCanvas(int nextPosition){
    previewValueInit();
    PreviewEditActivity.POSITION = nextPosition;
    isStampShown = false;
    CLICK_STATE.finish();
    previewItems.get(nextPosition).saved();
    invalidate();
  }

  public void previewValueInit(){
    previewPosWidth = 0;
    previewPosHeight = 0;
    previewWidth = 0;
    previewHeight = 0;
  }

  public boolean backPressed() {
    if(CLICK_STATE.getClickStateEnum() == ClickStateEnum.STATE_STAMP_EDIT){
      finishStampEdit();
      return true;
    }
    return false;
  }

  protected class SaveAllAsyncTask extends AsyncTask<Integer, Integer, String>{

    int nextPosition;
    final String ERROR_INVALID_POSITION = "ERROR_INVALID_POSITION";
    final String ERROR_IO_EXCEPTION = "ERROR_IO_EXCEPTION";

    @Override
    protected void onPreExecute() {
      super.onPreExecute();
    }

    @Override
    protected String doInBackground(Integer... param) {
      /**
       * 저장시 할 일
       * 1. 이미지를 원래 크기로 다시 확대(or 축소)
       * 2. 그 확대된 비율과, 낙관이 원래 붙어있던 위치에 맞춰서 확대된 이미지에도 제대로 낙관 붙이고
       * 3. 크기 맞춰서 자르고 (3000*3000을 프리뷰 원래 크기에 맞춰서 자른다는 뜻)
       * 4. 그 상태로 이미지로 저장
       * 5. 다시 축소해서 되돌리기
       */
      nextPosition = param[0];

      if (PreviewEditActivity.POSITION == -1) return ERROR_INVALID_POSITION;
      int previewPosition = PreviewEditActivity.POSITION;

      Bitmap screenshot = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(screenshot);
      mActivity.getmPreviewCanvasView().draw(canvas);

      Uri resultUri = previewItems.get(previewPosition).getResultImageURI();
      String resultFilePath = resultUri.getPath();
      File resultFile = new File(resultFilePath);
      FileOutputStream fos;
      try {
        fos = new FileOutputStream(resultFile);
        screenshot.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        fos.close();

        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(resultUri);
        mActivity.sendBroadcast(mediaScanIntent);

      }  catch (IOException e) {
        e.printStackTrace();
        return ERROR_IO_EXCEPTION;
      }

      CLICK_STATE.clickSave();

      PreviewItem previewItem = previewItems.get(previewPosition);
      previewItem.setOriginalImageURI(resultUri);
      previewItem.saved();

      return resultFilePath;
    }

    @Override
    protected void onPostExecute(String str) {
      if(str != ERROR_INVALID_POSITION) {

        if (nextPosition != -1){
          changeAndInitPreviewInCanvas(nextPosition);
        } else {
          changeAndInitPreviewInCanvas(PreviewEditActivity.POSITION);
        }


        if (str == ERROR_IO_EXCEPTION){
          Snackbar.make(mActivity.getCurrentFocus(), "저장 실패!", Snackbar.LENGTH_LONG).show();
        } else {
          File resultFile = new File(str);
          Snackbar.make(mActivity.getCurrentFocus(),
            "저장 폴더 : " + MainActivity.PREVIEW_SAVED_DIRECTORY + "\n파일 이름 : " + resultFile.getName(),
            Snackbar.LENGTH_LONG)
            .setAction("NEXT", new OnClickListener() {
              @Override
              public void onClick(View v) {
                if(PreviewEditActivity.POSITION + 1 < previewItems.size()){
                  nextPosition = PreviewEditActivity.POSITION + 1;
                  changeAndInitPreviewInCanvas(nextPosition);
                }
              }
            })
            .show();
        }
      }
      saveEnd();
      super.onPostExecute(str);
    }
  }
}
