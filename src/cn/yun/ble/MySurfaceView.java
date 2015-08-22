package cn.yun.ble;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.Button;
import android.widget.RelativeLayout;

public class MySurfaceView extends SurfaceView implements SurfaceHolder.Callback{

	private static final String TAG = "MySurfaceview";
    private static final boolean D = true;
    public static void log(String msg){
    	if(D){
    		Log.e(TAG, msg);
    	}
    }
	private Context context;
	private SurfaceHolder mSurfaceHolder;
	private int client_width;
	private int client_height;
	//圆盘中心坐标、半径、颜色
	private float cenx;
	private float ceny;
	private float CLOCK_RADIO;
	private int OUT_DIAL_COLOR = getResources().getColor(R.drawable.green);
	private int INNER_DIAL_COLOR = getResources().getColor(R.drawable.light_blue);
	private int radius;   //内圆半径
	private float TEXT_RADIO;
	//当前触碰坐标
	private float touchX;
	private float touchY;
	//touch down时的x，y值
	private float startX;
	private float startY;
	//内圆盘静态角度 
	private float initAngle = (float) (Math.PI/2);
	//
	private float totalRads = 0;   //累计转过的角度，顺时针为正，逆时针为负，弧度制
	private int circle = 0;
	private float currValue = 0;
	private float previousValue = 0;
	//一圈共有多少个刻度 
	private float totalIndex = 360;
	//单位刻度角度（角度制）
	private float minAngle = (float) (2*Math.PI/totalIndex);
	
	boolean isTouch;
	//绘图变量
	Canvas canvas;
	Paint OuterDialpaint;
	Paint bgPaint;
	Paint innerDialPaint;
	Paint pointPaint;
	Paint fbPaint;
	Paint txtPaint;
	
	public MySurfaceView(Context context){
		this(context,null);
	}
	
	public MySurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        mSurfaceHolder = this.getHolder();
		mSurfaceHolder.addCallback(this);
		this.setOnTouchListener(touchListener);
    }
	
	//paint 的初始化及样式设置
	private void initPaint() {   
			//外圆
			OuterDialpaint = new Paint();
			OuterDialpaint.setAntiAlias(true);
			OuterDialpaint.setColor(OUT_DIAL_COLOR);
			OuterDialpaint.setStyle(Paint.Style.STROKE);
			OuterDialpaint.setStrokeWidth(1);
			//外圆
			txtPaint = new Paint();
			txtPaint.setAntiAlias(true);
			txtPaint.setColor(OUT_DIAL_COLOR);
			txtPaint.setStyle(Paint.Style.STROKE);
			txtPaint.setStrokeWidth(5);
			txtPaint.setTextSize(50);

	        //背景
	        bgPaint = new Paint();
	        bgPaint.setColor(getResources().getColor(R.drawable.dark_grey2));
	        //内圆
	        innerDialPaint = new Paint();
	    	innerDialPaint.setAntiAlias(true);
	        innerDialPaint.setColor(INNER_DIAL_COLOR);
	        innerDialPaint.setStyle(Paint.Style.FILL);
	        //阴影
//	        innerDialPaint.setShadowLayer(100, -10, -10, 0Xff00ff00);
	        
	        //指针
	        pointPaint = new Paint();
	    	pointPaint.setAntiAlias(true);
	        pointPaint.setColor(Color.BLACK);
	        pointPaint.setStyle(Paint.Style.FILL);
	        //反馈点
	        int fbColor = 0x8792ACC6;
	        fbPaint = new Paint();
	        fbPaint.setColor(fbColor);
	        fbPaint.setStyle(Paint.Style.FILL);
			
		}
		
	public void init(){
		CLOCK_RADIO = Math.min(client_width, client_height)/2-50;
		radius = (int) (CLOCK_RADIO*0.7);
		cenx = client_width/2;
		ceny = client_height/2;    // _test 中心位置可调整
		TEXT_RADIO = CLOCK_RADIO + 30;
		initStartBtn();
	}
	public void initStartBtn() {
		//中心button start 布局
		int startBtnRadius = radius/4;
		Button startBtn = (Button) ((Activity)context).findViewById(R.id.btn_start);
		MarginLayoutParams margin=new MarginLayoutParams(startBtn.getLayoutParams());
		margin.setMargins((int)(cenx-startBtnRadius),(int)(ceny-startBtnRadius), (int)(cenx+margin.width), (int)(ceny+margin.height));
		RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(margin); 
		layoutParams.height = startBtnRadius*2;
		layoutParams.width = startBtnRadius*2;
		startBtn.setLayoutParams(layoutParams);
	}
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		init();
		initPaint();
		drawImg(initAngle);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
	}

	public void drawImg(float drawArg) {
		canvas = mSurfaceHolder.lockCanvas();
		try{
		//清屏
		canvas.drawColor(R.drawable.dark_grey2);
		drawBg();
		//画转盘
		drawInnerDial(drawArg);
		drawOuterDial();
        //数据显示
		displayAngle();
        
        //绘制反馈点
        if(isTouch){
	        canvas.drawCircle(touchX, touchY, 30, fbPaint);
        }
		}catch(NullPointerException e){
			//吞了退出异常
			return;
		}
		mSurfaceHolder.unlockCanvasAndPost(canvas);
		
	}
	//显示旋转角度
	private void displayAngle() {
		// TODO Auto-generated method stub
		((Activity)context).runOnUiThread(new Runnable(){
			@Override
			public void run() {
				TopBar angleTv =  (TopBar) ((Activity)context).findViewById(R.id.bar_angle);				
				angleTv.setContentText(getAngle()+" °");
			}
		});
	}

	/**
	 * 绘制外圆及刻度
	 */
	private void drawOuterDial(){
		
		canvas.drawCircle(cenx, ceny, CLOCK_RADIO, OuterDialpaint);
		
		for(int i = 0; i < totalIndex/2; i++){
			Path path = new Path();
			float r;
			OuterDialpaint.setColor(OUT_DIAL_COLOR);
			if(i%5 == 0) {
                r = CLOCK_RADIO-20;
                //针对0、90、180、270绘制数字及标记为特殊刻度（red）
                if(i % 90 == 0){
	                Paint textPaint = new Paint( Paint.ANTI_ALIAS_FLAG);  
	                textPaint.setTextSize(15);  
	                textPaint.setColor(Color.WHITE);  
	                float x5 = (float) (TEXT_RADIO*Math.cos(Math.PI/2-i*minAngle)+cenx);
	    			float y5 = (float) (ceny - TEXT_RADIO*Math.sin(Math.PI/2 - i*minAngle));
	    			float x6 = (float) (TEXT_RADIO*Math.cos(Math.PI/2-i*minAngle-Math.PI)+cenx);
	    			float y6 = (float) (ceny - TEXT_RADIO*Math.sin(Math.PI/2-i*minAngle-Math.PI));
	    			canvas.drawText(i+"", x5, y5, textPaint);
	    			canvas.drawText((i+180)+"", x6, y6, textPaint);
	    			
	    			OuterDialpaint.setColor(getResources().getColor(R.drawable.red));
                }
            }
            else {
                r = CLOCK_RADIO-10;
            }
			float x1 = (float) (CLOCK_RADIO*Math.cos(Math.PI/2-i*minAngle)+cenx);
			float y1 = (float) (ceny - CLOCK_RADIO*Math.sin(Math.PI/2-i*minAngle));
			float x2 = (float) (r*Math.cos(Math.PI/2-i*minAngle)+cenx);
			float y2 = (float) (ceny - r*Math.sin(Math.PI/2-i*minAngle));

			float x3 = (float) (CLOCK_RADIO*Math.cos(Math.PI/2-i*minAngle-Math.PI)+cenx);
			float y3 = (float) (ceny - CLOCK_RADIO*Math.sin(Math.PI/2-i*minAngle-Math.PI));
			float x4 = (float) (r*Math.cos(Math.PI/2-i*minAngle-Math.PI)+cenx);
			float y4 = (float) (ceny - r*Math.sin(Math.PI/2-i*minAngle-Math.PI));
			
			path.moveTo(x1, y1);
			path.lineTo(x2, y2);
			path.moveTo(x3, y3);
			path.lineTo(x4, y4);
            canvas.drawPath(path, OuterDialpaint);
		}
	}

	private void drawBg( ){
        canvas.drawRect(new Rect(0,0, client_width, client_height), bgPaint);
	}
    
    public void drawInnerDial(float degrees){

    	innerDialPaint.setStyle(Paint.Style.FILL);
    	Path path = new Path();
    	path.addCircle(cenx, ceny, radius, Path.Direction.CW);
    	path.addCircle(cenx, ceny, (float) (radius*0.3), Path.Direction.CCW);
    	canvas.drawPath(path, innerDialPaint);
    	drawPointer(degrees);
    	drawTestLine(degrees);

    	}
    private void drawPointer(float degrees){
    	
    	float x1 = (float) (radius*Math.cos(degrees) + cenx);
        float y1 = (float) (ceny - radius*Math.sin(degrees));
        float x2 = (float) (0.9*radius*Math.cos(degrees+0.05) + cenx);
        float y2 = (float) (ceny - 0.9*radius*Math.sin(degrees+0.05));
        float x3 = (float) (0.9*radius*Math.cos(degrees-0.05) + cenx);
        float y3 = (float) (ceny - 0.9*radius*Math.sin(degrees-0.05));
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        canvas.drawPath(path, pointPaint);
    }
    private void drawTestLine(float degrees){
    	Paint paint = new Paint();
    	paint.setAntiAlias(true);
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
    	float x1 = (float) (radius*Math.cos(degrees) + cenx);
        float y1 = (float) (ceny - radius*Math.sin(degrees));
        float x2 = (float) (CLOCK_RADIO*Math.cos(degrees) + cenx);
        float y2 = (float) (ceny - CLOCK_RADIO*Math.sin(degrees));
        canvas.drawLine(x1, y1, x2, y2, paint);
    }
    private OnTouchListener touchListener = new OnTouchListener() {

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			touchX = event.getX();
            touchY = event.getY();
			if (event.getAction() == MotionEvent.ACTION_DOWN)
            {   
				startX = touchX;
				startY = touchY;
				isTouch = true;
                return true;  
            }  
            else if (event.getAction() == MotionEvent.ACTION_UP)  
            {
            	//重画，去除反馈点
            	isTouch = false;
            	if(MainActivity.isControl == true){
            	new Thread(new Runnable(){
					@Override
					public void run() {
						// TODO Auto-generated method stub
						float degrees = totalRads;
						if(totalRads > 0){
							while(totalRads > 0){
								try {
									Thread.sleep(1);
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								drawImg((float) (Math.PI/2 - totalRads));
								totalRads -= 0.5;
							}
						}else if(totalRads < 0){
							while(totalRads < 0){
								try {
									Thread.sleep(1);
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								drawImg((float) (Math.PI/2 - totalRads));
								totalRads += 0.5;
							}
						}
						reset();
					}
            		
            	}).start();
            	}
            	else{
            		initAngle = initAngle - calculateAngle();
                	drawImg(initAngle);
            	}
                return true;  
            }
            else if (event.getAction() == MotionEvent.ACTION_MOVE)  
            {
            	//计算角度
            	float offset = calculateAngle();
            	float currAngle = initAngle - offset;  //当前指针所在角度
            	//将其转换成  0~2*PI范围 （按照逆时针计算的角度），此时三点钟方向为0度
            	if(currAngle < 0){
            		currAngle += 2*Math.PI;
        		}else{
        			currAngle = (float) (currAngle%(2*Math.PI));
        		}
            	//因为设定了90°处为0值，计算当前角度与90°的夹角  （按顺时针算）
        		currValue = (float) (Math.PI/2 - currAngle);
        		if(currValue < 0){
        			currValue = (float) (currValue + 2*Math.PI);
        		}
	        	//是否过零点
	        	if((previousValue > 4 && previousValue < 7) && (currValue < 1)){
	        			circle++;
	        	}else if((currValue > 4 && currValue < 7) && (previousValue < 1)){
	        			circle--;
	        	}
	        	previousValue = currValue;
	        	
        		totalRads = (float) (circle*2*Math.PI + currValue);

            	//绘图
            	drawImg(initAngle - offset);
                return true;  
            }  
			return false;
		}
	};
	/**
	 * 计算旋转角度
	 * 根据起始位置角度（数学中坐标正x的角度）与当前位置角度的差得到夹角
	 * @return
	 */
	private float calculateAngle(){
		float[] startPos = getChangePositon(startX, startY);
		float[] endPos = getChangePositon(touchX,touchY);
		float startAngle = 0;
		float endAngle = 0;
		switch(getQuadrant(startPos)){
		case 1:startAngle = (float) Math.atan(startPos[1]/startPos[0]);break;
		case 2:
		case 3:startAngle = (float) (Math.PI + Math.atan(startPos[1]/startPos[0]));break;
		case 4:startAngle = (float) (2*Math.PI + Math.atan(startPos[1]/startPos[0]));break;
		}
		switch(getQuadrant(endPos)){
		case 1:endAngle = (float) Math.atan(endPos[1]/endPos[0]); break;
		case 2:
		case 3:endAngle = (float) (Math.PI + Math.atan(endPos[1]/endPos[0]));break;
		case 4:endAngle = (float) (2*Math.PI + Math.atan(endPos[1]/endPos[0]));break;
		}
		return startAngle - endAngle;
	}
	private float[] getChangePositon(float x, float y){
		float[] ret = new float[2];
		ret[0] = x - cenx;
		ret[1] = ceny - y;
		return ret;
	}
	
	/**
	 * 返回坐标所属象限
	 * @param pos[0] 横坐标  pos[1]纵坐标
	 * @return 1，2,3,4 象限
	 */
	private int getQuadrant(float[] pos){
		if(pos[0] > 0 && pos[1] > 0) return 1;   //第一象限
		else if(pos[0] < 0 && pos[1] > 0) return 2;
		else if(pos[0] < 0 && pos[1] < 0) return 3;
		else return 4;
	}
	
	
	//MainAcivity调用
	public void reset(){
		totalRads = 0;
		circle = 0;
		initAngle = (float) (Math.PI/2);
		previousValue = 0f;
		currValue = 0f;
		drawImg(initAngle);
		
	}
	/**
	 * 返回角度制的旋转角度，并取整
	 * @return
	 * UIActivity需要调用
	 */
	public int getAngle(){
		//return (float) (Math.floor(totalRads/minAngle*100)/100f);
		return Math.round(totalRads/minAngle);
//		return (int) Math.round(totalRads*180/Math.PI);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
		int measuredHeight = MeasureSpec.getSize(heightMeasureSpec);
		//获取控制界面surfaceview的宽高
		client_width = measuredWidth;
		client_height = measuredHeight;
		setMeasuredDimension(measuredWidth, measuredHeight);
	}
}
