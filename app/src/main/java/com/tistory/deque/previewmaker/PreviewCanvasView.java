package com.tistory.deque.previewmaker;

import android.app.Activity;
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
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class PreviewCanvasView extends View {
  private final static String TAG = "PreviewEditActivity";
  private enum ZoomClickWhere {
    NONE,
    RIGHT_TOP,
    RIGHT_BOTTOM,
    LEFT_TOP,
    LEFT_BOTTOM
  }
  private static ZoomClickWhere ZOOM_CLICK_WHERE = ZoomClickWhere.NONE;

  private static ClickState CLICK_STATE;

  private Canvas mCanvas;
  private PreviewEditActivity mActivity;
  private int canvasWidth, canvasHeight;
  public static int grandParentWidth, grandParentHeight;

  ArrayList<PreviewItem> previewItems;
  private int previewPosWidth, previewPosHeight;
  private int previewPosWidthDelta, previewPosHeightDelta;
  private int previewWidth, previewHeight;
  private double previewZoomRate = 1;

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
  private boolean canMoveStamp = false;


  public PreviewCanvasView(Context context, PreviewEditActivity activity, ArrayList<PreviewItem> previewItems) {
    super(context);
    mActivity = activity;
    this.previewItems = previewItems;
    CLICK_STATE = ClickState.getClickState();
    CLICK_STATE.start();
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

    this.setMeasuredDimension(3000, 3000);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    /**
     * 멀티터치를 통한 확대/회전 구현해야함
     * 프리뷰는 확대만, 스탬프는 확대 회전 둘다
     * 스탬프의경우 삭제도 만들어야됨
     */
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
    Logger.d(TAG, "touch x, y : " + x + ", " + y);
    Logger.d(TAG, "touch sw, sh, swp, shp : " + stampWidth + ", " + stampHeight + ", " + stampWidthPos + ", " + stampHeightPos);

    movePrevX = x;
    movePrevY = y;

    if(isTouchInStamp(x,y)){
      CLICK_STATE.clickStamp();
    }
    if((ZOOM_CLICK_WHERE = isTouchStampZoom(x,y)) != ZoomClickWhere.NONE){
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
    ZOOM_CLICK_WHERE = ZoomClickWhere.NONE;
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

  private ZoomClickWhere isTouchStampZoom(int x, int y){
    int radius = (int) (stampGuideCircleRadius + 15);
    int x_s = stampWidthPos; //x start
    int x_e = stampWidthPos + stampWidth; //x end
    int y_s = stampHeightPos; // y start
    int y_e = stampHeightPos + stampHeight; // y end

    if (isInBoxWithRadius(x, y, x_s, y_s, radius)) {
      return ZoomClickWhere.LEFT_TOP;
    } else if (isInBoxWithRadius(x, y, x_s, y_e, radius)) {
      return ZoomClickWhere.LEFT_BOTTOM;
    } else if (isInBoxWithRadius(x, y, x_e, y_s, radius)) {
      return ZoomClickWhere.RIGHT_TOP;
    } else if (isInBoxWithRadius(x, y, x_e, y_e, radius)) {
      return ZoomClickWhere.RIGHT_BOTTOM;
    } else {
      return ZoomClickWhere.NONE;
    }
  }
  private boolean isTouchInPreview(int x, int y) {
    return isInBoxWithWidth(x, y, previewPosWidth, previewPosHeight, previewWidth, previewHeight);
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

    if(rate > 1 && previewBitmapWidth > canvasWidth) { // w > h

      previewPosWidth = 0;
      previewPosHeight = (canvasHeight - (int) (canvasWidth * (1 / rate))) / 2;
      previewWidth = canvasWidth;
      previewHeight = (int) (canvasWidth * (1 / rate));

    } else if (rate <= 1 && previewBitmapHeight > canvasHeight) { // w < h

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

    previewPosWidth += previewPosWidthDelta;
    previewPosHeight += previewPosHeightDelta;

    dst = new Rect(previewPosWidth, previewPosHeight, previewPosWidth + previewWidth, previewPosHeight + previewHeight);
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
    invalidate();
  }

  protected void showStamp(){
    setStampShown(true);
    CLICK_STATE.clickStampButton();
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
      stampItem.setWidth(stampWidth);
      stampItem.setHeight(stampHeight);
      mActivity.stampWidthHeightUpdate(id, stampWidth, stampHeight);
    }

    stampRate = (double) stampWidth / (double) stampHeight;

    stampWidthPos = (stampPosWidthPer * canvasWidth / 100) - (stampWidth / 2);
    stampHeightPos = (stampPosHeightPer * canvasHeight / 100) - (stampHeight / 2);

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

  public void savePreviewAll(){
    //SaveAllAsyncTask saveAllAsyncTask = new SaveAllAsyncTask();
    //saveAllAsyncTask.execute(previewItems.size());
  }

  public void savePreviewEach(int previewPosition, PreviewCanvasView v){
    /**
     * 저장시 할 일
     * 1. 이미지를 원래 크기로 다시 확대(or 축소)
     * 2. 그 확대된 비율과, 낙관이 원래 붙어있던 위치에 맞춰서 확대된 이미지에도 제대로 낙관 붙이고
     * 3. 크기 맞춰서 자르고 (3000*3000을 프리뷰 원래 크기에 맞춰서 자른다는 뜻)
     * 4. 그 상태로 이미지로 저장
     * 5. 다시 축소해서 되돌리기
     */
    PreviewEditActivity.POSITION = previewPosition;
    v.callInvalidate();

    Bitmap screenshot = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(screenshot);
    v.draw(canvas);

    Uri resultUri = previewItems.get(previewPosition).getResultImageURI();
    String resultFilePath = resultUri.getPath();
    File resultFile = new File(resultFilePath);
    FileOutputStream fos;
    try{
      fos = new FileOutputStream(resultFile);
      screenshot.compress(Bitmap.CompressFormat.JPEG, 100, fos);
      fos.close();
      Snackbar.make(this, "저장 성공 : " + resultFilePath, Snackbar.LENGTH_LONG).show();

      Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
      mediaScanIntent.setData(resultUri);
      mActivity.sendBroadcast(mediaScanIntent);

    } catch (FileNotFoundException e) {
      e.printStackTrace();
      Snackbar.make(this, "저장 실패...", Snackbar.LENGTH_LONG).show();
    } catch (IOException e) {
      e.printStackTrace();
      Snackbar.make(this, "저장 실패...", Snackbar.LENGTH_LONG).show();
    }

    CLICK_STATE.clickSave();

    PreviewItem previewItem = previewItems.get(PreviewEditActivity.POSITION);
    previewItem.setOriginalImageURI(resultUri);
    previewItem.saved();
  }

  public void clickNewPreview(final int nextPosition) {
    AlertDialog.Builder stampDeleteAlert = new AlertDialog.Builder(mActivity);
    stampDeleteAlert.setMessage("편집 중인 프리뷰를 저장하시겠어요?").setCancelable(true)
      .setPositiveButton("YES", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          savePreviewEach(PreviewEditActivity.POSITION, PreviewCanvasView.this);
          changePreviewInCanvas(nextPosition);
          return;
        }
      })
      .setNegativeButton("NO",
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            changePreviewInCanvas(nextPosition);
            return;
          }
        });

    if(PreviewEditActivity.POSITION != -1){
      AlertDialog alert = stampDeleteAlert.create();
      alert.show();
    }
  }

  public void changePreviewInCanvas(int nextPosition){
    previewValueInit();
    PreviewEditActivity.POSITION = nextPosition;
    isStampShown = false;
    invalidate();
  }
  public void previewValueInit(){
    previewPosWidth = 0;
    previewPosHeight = 0;
    previewPosWidthDelta = 0;
    previewPosHeightDelta = 0;
    previewWidth = 0;
    previewHeight = 0;
    previewZoomRate = 1;
  }

  public boolean backPressed() {
    if(CLICK_STATE.getClickStateEnum() == ClickStateEnum.STATE_STAMP_EDIT){
      finishStampEdit();
      return true;
    }
    return false;
  }

  protected class SaveAllAsyncTask extends AsyncTask<Integer, Integer, Integer>{
    @Override
    protected Integer doInBackground(Integer... integers) {
      for(int i = 0 ; i < integers[0] ; i ++){
        savePreviewEach(i, PreviewCanvasView.this);
      }
      return null;
    }
  }
}
