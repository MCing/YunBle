package cn.yun.ble;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import Protocol.BLEProtocol;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AnticipateOvershootInterpolator;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import cn.wheel.widget.NumericWheelAdapter;
import cn.wheel.widget.WheelView;
import cn.yun.ble.TopBar.TopBarClickListener;

@SuppressLint("NewApi")
public class MainActivity extends Activity implements OnClickListener{
	private static final String TAG = "YunBle";
    private static final boolean D = true;
    public static void log(String msg){
    	if(D){
    		Log.e(TAG, msg);
    	}
    }

	//蓝牙4.0的UUID,其中0000ffe1-0000-1000-8000-00805f9b34fb是广州汇承信息科技有限公司08蓝牙模块的UUID
		public static String HEART_RATE_MEASUREMENT = "0000ffe1-0000-1000-8000-00805f9b34fb";
		public static String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
		public static String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
		public static String EXTRAS_DEVICE_RSSI = "RSSI";
		//蓝牙连接状态
		private boolean mIsConnected = false;
		private static final int CONNECTED = 1;
		private static final int DISCONNECTED = 0;
		//蓝牙名字
		private String mDeviceName;
		//蓝牙地址
		private String mDeviceAddress;
		//蓝牙信号值
		private String mRssi;
		//蓝牙service,负责后台的蓝牙服务
		public static BluetoothLeService mBluetoothLeService;
		
		//文本框，显示接受的内容
		private TextView connect_state;

		private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
		//蓝牙特征值
		private BluetoothGattCharacteristic target_chara = null;
		private Handler mhandler = new Handler();
	//控制功能
	private Handler ctrlListenHandler;
	private HandlerThread ctrlListenThread;
	private HandlerThread timeCounterThread;
	private Handler timeCounterHandler;
	private boolean mTimeCounterThreadFlag;
	//layout view
	private MySurfaceView surface;
	private Button ctrlBtn;
	private Button startBtn;
	private TextView timeLeftTv;
	private TextView taskTv;
	private ToggleButton togBtn;
	private TopBar durationBar;
	private TopBar angleBar;
	private Button scanBtn;
	private Button langBtn;
	private Dialog mDialog;
	
	//参数
	private int angle;
	private int duration = 60;  //时长，初始值为60秒
	private int leftTime;       //运行剩余时间，单位为秒
	private float leftAngle;   //剩余角度： 角度制 单位度
	private float deSpeed;		//速度 ：单位  度/秒
	//控制标识
	public static boolean isControl;
	private boolean isStart;
	private boolean isBound = false;
	//电机的极限速度  单位为   ：度/秒
	private float mMotorLimit = 19.6f;
	//ble设备 任务指令
	public static BLEProtocol task = new BLEProtocol();
	//接收BLE设备返回的数据，有可能不能一次接收完
	private StringBuilder recvBuilder = new StringBuilder();
	
	private static final int REQUEST_CONNECT_DEVICE = 1;
	//存放语言的键值
	private static final String PRELANGUAGE = "YUNBLELANGUAGE";
	
	@Override
 	protected void onCreate(Bundle savedInstanceState) {
		initLanguage();
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
               WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_main);
		initView();
		setDuraText();
		initThread();
		isControl = false;
		
	}
	private void initLanguage() {
		PreferenceUtil.init(this);
		//根据上次的语言设置，重新设置语言
		String defLang = PreferenceUtil.getString(PRELANGUAGE, null);
		if(defLang != null){
			switchLanguage(defLang);
		}
		else {
			Resources resources = getResources();
	        Configuration config = resources.getConfiguration();
	        if(config.locale.equals(Locale.SIMPLIFIED_CHINESE)) {
	        	PreferenceUtil.commitString(PRELANGUAGE, "zh");
	        } else {
	        	PreferenceUtil.commitString(PRELANGUAGE, "en");
	        }
		}
	}
	private void initThread() {
		//初始化控制需要的线程
		ctrlListenThread = new HandlerThread("ctrlListenThread");
		ctrlListenThread.start();
		ctrlListenHandler = new Handler(ctrlListenThread.getLooper(), ctrlListenCallback);
		
		timeCounterThread = new HandlerThread("timeCounterThread");
		timeCounterThread.start();
		timeCounterHandler = new Handler(timeCounterThread.getLooper(), timeCounterCallback);
	}
	private void initBle(Intent data) {
		Bundle b = data.getExtras();
		//从意图获取显示的蓝牙信息
		mDeviceName = b.getString(EXTRAS_DEVICE_NAME);
		mDeviceAddress = b.getString(EXTRAS_DEVICE_ADDRESS);
		mRssi = b.getString(EXTRAS_DEVICE_RSSI);
		/* 启动蓝牙service */
		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
	}
	private void initView() {
		//获取自定义控件
		durationBar = (TopBar) findViewById(R.id.bar_duration);  //id是在acitivity_main中定义的
		durationBar.setOnTopBarClickListener(new TopBarClickListener() {
			@Override
			public void leftclick(){   //选择时长
				showPickTimeDialog();
			}
		});
		angleBar = (TopBar) findViewById(R.id.bar_angle);  //id是在acitivity_main中定义的
		angleBar.setOnTopBarClickListener(new TopBarClickListener() {
			@Override
			public void leftclick(){   //归零
				surface.reset();    
			}
		});
		langBtn = (Button) findViewById(R.id.btn_language);
		langBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if (mDialog == null) {
					LayoutInflater inflater = getLayoutInflater();
					View layout = inflater.inflate(R.layout.dialog_select_lanuage,null);
					TextView english = (TextView) layout.findViewById(R.id.select_english);
					TextView chinese = (TextView) layout.findViewById(R.id.select_chinese);
					mDialog = new Dialog(MainActivity.this, R.style.Custom_Dialog_Theme);
					english.setOnClickListener(MainActivity.this);
					chinese.setOnClickListener(MainActivity.this);
					mDialog.setContentView(layout);
				}
				mDialog.show();
			}
		});
		scanBtn = (Button) findViewById(R.id.btn_scan);
		scanBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				 Intent intent = new Intent(MainActivity.this, DeviceListActivity.class);
		         startActivityForResult(intent, REQUEST_CONNECT_DEVICE);
			}
		});
		ctrlBtn = (Button) findViewById(R.id.btn_ctrl);
		startBtn = (Button) findViewById(R.id.btn_start);
		connect_state = (TextView) findViewById(R.id.connect_state);
		timeLeftTv = (TextView) findViewById(R.id.tv_lefttime);
		taskTv = (TextView) findViewById(R.id.tv_task);
		surface = (MySurfaceView) findViewById(R.id.mysurfaceview);
		startBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if(!mIsConnected){
					Toast.makeText(MainActivity.this, R.string.connect_error, Toast.LENGTH_SHORT).show();
					return;
				}
				if(!isStart){
					angle = surface.getAngle();
					deSpeed = (float)angle/duration; 
					if(Math.abs(deSpeed) > mMotorLimit ){
						Toast.makeText(MainActivity.this, R.string.error, Toast.LENGTH_LONG).show();
						return;
					}
					if(deSpeed == 0f || angle == 0){
						return;
					}
					task.setAngle(angle);
					task.setDuration(duration);
					sendToBle(task.getCmd());
					startBtn.setText(R.string.stop);
					isStart = true;
					leftTime = duration;
					leftAngle = angle;
//					log("decrement speed:"+deSpeed);
					displayTask(duration, angle);
					startTimeCounterThread();
				}else{
					isStart = false;
					startBtn.setText(R.string.start);
					//停止正在执行的任务
					sendToBle(BLEProtocol.TERMINATION);  
					//剩余时间归零
					leftTime = 0;
					displayTask();
				}
			}
		});
		ctrlBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if(!mIsConnected){
					Toast.makeText(MainActivity.this, R.string.connect_error, Toast.LENGTH_SHORT).show();
					return;
				}
				if(isStart){  //提醒有任务正在运行是否
					Toast.makeText(MainActivity.this, R.string.ctrl_error, Toast.LENGTH_SHORT).show();
					return;
				}
				if(!isControl){
					v.setBackgroundColor(0xff00ff00);   //green
					isControl = true;
					surface.reset();
					//开启控制线程
					ctrlListenHandler.sendEmptyMessage(0);
					stopTimeCounterThread();
					//停止正在执行
					sendToBle(BLEProtocol.TERMINATION);
					//清除正在显示的任务
					leftTime = 0;
					displayTask();
				}
				else{
					isControl = false;
					v.setBackgroundColor(0xff5295E3);   //turn to oragal color
				}
				
			}
		});
		
	}
	@Override
	protected void onDestroy()
	{
		super.onDestroy();
        //解除广播接收器
		unregisterReceiver(mGattUpdateReceiver);
		mBluetoothLeService = null;
		//解除蓝牙服务
		if(isBound){
			unbindService(mServiceConnection);
		}
		//退出线程
		ctrlListenThread.quitSafely();
		stopTimeCounterThread();
		timeCounterThread.quitSafely();
	}

	// Activity出来时候，绑定广播接收器，监听蓝牙连接服务传过来的事件
	@Override
	protected void onResume()
	{
		super.onResume();
		//绑定广播接收器
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		if (mBluetoothLeService != null)
		{    
			//根据蓝牙地址，建立连接
			final boolean result = mBluetoothLeService.connect(mDeviceAddress);
		}
	}

	private void showPickTimeDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		LayoutInflater factory = LayoutInflater.from(this);
        final View textEntryView = factory.inflate(R.layout.dialog_picktime, null);
        initPickTime(textEntryView);
            builder.setTitle(R.string.picktimetip);
            builder.setView(textEntryView);
            builder.setNegativeButton(R.string.reset, 
            		new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                	initWheel(60);
                	//点击重置后滑轮归回初始化位置，对话框不退出
                	setQuitable(dialog, false);
                }
            });
            builder.setPositiveButton(R.string.confirm,
            		new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
            	 int days = wheelDay.getCurrentItem();
               	 int hours = wheelHour.getCurrentItem();
            	 int mins = wheelMin.getCurrentItem();
            	 int secs = wheelSec.getCurrentItem();
            	 duration = days*24*3600+hours*3600 + mins*60 + secs;
            	 setDuraText();
            	 setQuitable(dialog, true);
                }
            });
            builder.create().show();
		
	}
	/**
	 * 0~3分别为 天，小时，分钟，秒
	 * textview  输出时长(格式化)
	 */
	private void setDuraText(){
		int[] detail = secToDet(duration);
        durationBar.setContentText(String.format("%02d %02d : %02d : %02d", 
        		detail[0], 
        		detail[1],
        		detail[2],
        		detail[3]));
	}
	public int[] secToDet(long seconds){
		int[] det = new int[4];
		det[0] = (int) (seconds/(24*60*60));  //days
        det[1] = (int) ((seconds - det[0]*(24*60*60))/3600);  //hours
        det[2] = (int) ((seconds - det[0]*(24*60*60) - det[1]*3600)/60);  //minutes
        det[3] = (int) (seconds - det[0]*(24*60*60) - det[1]*3600 - det[2]*60);   //seconds
        return det;
	}
	
	/* BluetoothLeService绑定的回调函数 */
	private final ServiceConnection mServiceConnection = new ServiceConnection()
	{

		@Override
		public void onServiceConnected(ComponentName componentName,
				IBinder service)
		{
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service)
					.getService();
			if (!mBluetoothLeService.initialize())
			{
				finish();
			}
			// Automatically connects to the device upon successful start-up
			// initialization.
			// 根据蓝牙地址，连接设备
			mBluetoothLeService.connect(mDeviceAddress);
			isBound = true;

		}

		@Override
		public void onServiceDisconnected(ComponentName componentName)
		{
			mBluetoothLeService = null;
			isBound = false;
		}
	};

	/**
	 * 广播接收器，负责接收BluetoothLeService类发送的数据
	 */
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver()
	{
		int count = 0;
		@Override
		public void onReceive(Context context, Intent intent)
		{
			final String action = intent.getAction();
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action))//Gatt连接成功
			{
				mIsConnected = true;
				//更新连接状态
				updateConnectionState(CONNECTED);
			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED//Gatt连接失败
					.equals(action))
			{
				mIsConnected = false;
				updateConnectionState(DISCONNECTED);
				//清空任务栏
				leftTime = 0;
				displayTask();
				if(isControl){
					isControl = false;
					ctrlBtn.setBackgroundColor(0xff5295E3);   //turn to oragal color
				}
			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED//发现GATT服务器
					.equals(action))
			{
				// Show all the supported services and characteristics on the
				// user interface.
				//获取设备的所有蓝牙服务
				displayGattServices(mBluetoothLeService
						.getSupportedGattServices());
				System.out.println("BroadcastReceiver :"
						+ "device SERVICES_DISCOVERED");
			} else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action))//有效数据
			{    
				count++;
				//处理发送过来的数据  recv
				String recvMsg = intent.getExtras().getString(BluetoothLeService.EXTRA_DATA);
				log("recv:"+recvMsg);
				if(count == 1){
					//第一个信号是蓝牙回复的信号，接收到该信号后才可以发送
					//这里发送获取剩余时间，查询是否有未完成任务
					sendToBle(BLEProtocol.LEFTTIME);
				}
				if(recvBuilder.length()>0){  //未接收完毕
					int indexOfEnd = recvMsg.indexOf(";");
					recvBuilder.append(recvMsg.substring(0, indexOfEnd));
					handleMsg(recvBuilder.toString());
					log("builder append:"+recvBuilder.toString());
					recvBuilder.delete(0, recvBuilder.length());  //清空
				}else if(BLEProtocol.LEFTTIME.equals(recvMsg.substring(0, 1)) && recvBuilder.length()==0){
					int indexOfEnd = recvMsg.indexOf(";");
					if(indexOfEnd != -1){
						handleMsg(recvMsg.substring(0, indexOfEnd));
					}else{
						recvBuilder.append(recvMsg);
					}
				}
			}
		}
	};

	/* 更新连接状态 */
	private void updateConnectionState(final int state)
	{
		runOnUiThread(new Runnable(){

			@Override
			public void run() {
				if(state == CONNECTED){
					connect_state.setTextColor(0xff3FABCD);   //blue
					connect_state.setText(R.string.connected);
				}else{
					connect_state.setTextColor(0xffff0000);   //red
					connect_state.setText(R.string.disconnected);
				}
			}
		});
	}
	/**
	 * 处理设备返回的数据
	 * 格式 ：  Lx1Lx2Lx3Lx4
	 * L标识符，x1为剩余时间（ms）,x2位剩余角度(度),x3位设定的角度，x4位设定的时长
	 * @param msg
	 */
	protected void handleMsg(String msg) {
		String[] datas = msg.substring(1).split("L");
		if(datas.length != 4){
			return;
		}
		int tmpAngle = Integer.valueOf(datas[2]);
		if(tmpAngle == 0){  //empty task
			return;
		}
		leftTime = Math.round(Float.valueOf(datas[0])/1000);
		leftAngle = Float.valueOf(datas[1]);
		angle = tmpAngle;
		duration = Integer.valueOf(datas[3]);
		deSpeed = (float)angle/duration;
		displayTask(duration, angle);
		startTimeCounterThread();
	}

	/* 意图过滤器 */
	private static IntentFilter makeGattUpdateIntentFilter()
	{
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter
				.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		return intentFilter;
	}
	/** 
	* @Title: displayGattServices 
	* @Description: TODO(处理蓝牙服务) 
	* @param 无  
	* @return void  
	* @throws 
	*/ 
	@SuppressLint("NewApi")
	private void displayGattServices(List<BluetoothGattService> gattServices)
	{

		if (gattServices == null)
			return;
		String uuid = null;
		String unknownServiceString = "unknown_service";
		String unknownCharaString = "unknown_characteristic";

		// 服务数据,可扩展下拉列表的第一级数据
		ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();

		// 特征数据（隶属于某一级服务下面的特征值集合）
		ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData = new ArrayList<ArrayList<HashMap<String, String>>>();

		// 部分层次，所有特征值集合
		mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

		// Loops through available GATT Services.
		for (BluetoothGattService gattService : gattServices)
		{

			// 获取服务列表
			HashMap<String, String> currentServiceData = new HashMap<String, String>();
			uuid = gattService.getUuid().toString();

			// 查表，根据该uuid获取对应的服务名称。SampleGattAttributes这个表需要自定义。

			gattServiceData.add(currentServiceData);

			System.out.println("Service uuid:" + uuid);

			ArrayList<HashMap<String, String>> gattCharacteristicGroupData = new ArrayList<HashMap<String, String>>();

			// 从当前循环所指向的服务中读取特征值列表
			List<BluetoothGattCharacteristic> gattCharacteristics = gattService
					.getCharacteristics();

			ArrayList<BluetoothGattCharacteristic> charas = new ArrayList<BluetoothGattCharacteristic>();

			// Loops through available Characteristics.
			// 对于当前循环所指向的服务中的每一个特征值
			for (final BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics)
			{
				charas.add(gattCharacteristic);
				HashMap<String, String> currentCharaData = new HashMap<String, String>();
				uuid = gattCharacteristic.getUuid().toString();

				if (gattCharacteristic.getUuid().toString()
						.equals(HEART_RATE_MEASUREMENT))
				{
					// 测试读取当前Characteristic数据，会触发mOnDataAvailable.onCharacteristicRead()
					mhandler.postDelayed(new Runnable()
					{

						@Override
						public void run()
						{
							// TODO Auto-generated method stub
							mBluetoothLeService
									.readCharacteristic(gattCharacteristic);
						}
					}, 200);

					// 接受Characteristic被写的通知,收到蓝牙模块的数据后会触发mOnDataAvailable.onCharacteristicWrite()
					mBluetoothLeService.setCharacteristicNotification(
							gattCharacteristic, true);
					target_chara = gattCharacteristic;
					// 设置数据内容
					// 往蓝牙模块写入数据
					// mBluetoothLeService.writeCharacteristic(gattCharacteristic);
				}
				List<BluetoothGattDescriptor> descriptors = gattCharacteristic
						.getDescriptors();
				for (BluetoothGattDescriptor descriptor : descriptors)
				{
					System.out.println("---descriptor UUID:"
							+ descriptor.getUuid());
					// 获取特征值的描述
					mBluetoothLeService.getCharacteristicDescriptor(descriptor);
				}

				gattCharacteristicGroupData.add(currentCharaData);
			}
			// 按先后顺序，分层次放入特征值集合中，只有特征值
			mGattCharacteristics.add(charas);
			// 构件第二级扩展列表（服务下面的特征值）
			gattCharacteristicData.add(gattCharacteristicGroupData);
		}

	}
	
	private Handler.Callback ctrlListenCallback = new Handler.Callback() { 
        //该接口的实现就是处理异步耗时任务的，因此该方法执行在子线程中
        @Override
        public boolean handleMessage(Message msg) { 
        	boolean isTerminal = true;
            switch (msg.what) {
            case 0:     //开启任务
            	while(isControl){
    				//监听间隔
    				try {
    					Thread.sleep(500);
    				} catch (InterruptedException e) {
    					e.printStackTrace();
    				}
    				//设定固定偏移角度
    				int angle = surface.getAngle();
    				if(angle == 0){
    					if(!isTerminal){
    						sendToBle(BLEProtocol.TERMINATION);
    						isTerminal = true;
    					}
    					continue;
    				}else{
    					task.setAngle(angle);
    					task.setDuration(5);   //调整这个值可以使不同的角度配不同速度（5比较明显）
    					sendToBle(task.getCmd());
    					isTerminal = false;
    				}
    			    }
                break;
 
            default:
                break;
            }
 
            return false;
        }
    };
    /**
     * 计时线程
     */
    private Handler.Callback timeCounterCallback = new Handler.Callback() { 
        @Override
        public boolean handleMessage(Message msg) { 
            	while(mTimeCounterThreadFlag){
    				try {
						Thread.sleep(1000);
						leftTime--;
						leftAngle -= deSpeed;
						if(leftTime >= 0){
							displayTask();
						}else{
							stopTimeCounterThread();
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
    				
    			    }
            return false;
        }
    };
    /**
     * picktime wheel控件相关
     */
	private WheelView wheelDay;
	private WheelView wheelHour;
	private WheelView wheelMin;
	private WheelView wheelSec;
	private void initPickTime(View view){
		wheelDay = getWheel(view, R.id.days);
		wheelHour = getWheel(view, R.id.hours);
		wheelMin = getWheel(view, R.id.minutes);
		wheelSec = getWheel(view, R.id.secs);
		initWheel(duration);
	}
	@SuppressLint("NewApi")
	private void initWheel(long seconds) {
		int[] detail = secToDet(seconds);
		wheelDay.setAdapter(new NumericWheelAdapter(0, 100));
		wheelHour.setAdapter(new NumericWheelAdapter(0, 23));
		wheelMin.setAdapter(new NumericWheelAdapter(0, 59));
		wheelSec.setAdapter(new NumericWheelAdapter(0, 59));
		wheelDay.setCurrentItem(detail[0]);
		wheelHour.setCurrentItem(detail[1]);
		wheelMin.setCurrentItem(detail[2]);
		wheelSec.setCurrentItem(detail[3]);
		
		wheelDay.setCyclic(true);
		wheelDay.setInterpolator(new AnticipateOvershootInterpolator());  
		wheelHour.setCyclic(true);
		wheelHour.setInterpolator(new AnticipateOvershootInterpolator());
		wheelMin.setCyclic(true);
		wheelMin.setInterpolator(new AnticipateOvershootInterpolator());
		wheelSec.setCyclic(true);
		wheelSec.setInterpolator(new AnticipateOvershootInterpolator());
		
		wheelDay.setLabel(getResources().getString(R.string.days));
		wheelHour.setLabel(getResources().getString(R.string.hours));
		wheelMin.setLabel(getResources().getString(R.string.minutes));
		wheelSec.setLabel(getResources().getString(R.string.seconds));
		}
		private WheelView getWheel(View view, int id) {
		return (WheelView) view.findViewById(id);
	}
	/**
	 * 设置点击对话框按钮后是否退出对话框（重置选项需要不退出）
	 * @param dialog
	 * @param quit  true：退出  false：不退出
	 */
	private void setQuitable(DialogInterface dialog, boolean quit){
		try  
        {  
            Field field = dialog.getClass().getSuperclass().getDeclaredField("mShowing");  
            field.setAccessible(true);  
             //设置mShowing值，欺骗android系统  
            field.set(dialog, quit);  
        }catch(Exception e) {  
            e.printStackTrace();  
        }  
	}
	/**
	 * 向BLE设备发送指令
	 * @param cmd
	 */
	private void sendToBle(String cmd){
		try{
			target_chara.setValue(cmd);
			//调用蓝牙服务的写特征值方法实现发送数据
			mBluetoothLeService.writeCharacteristic(target_chara);
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	private void startTimeCounterThread(){
			mTimeCounterThreadFlag = true;
			timeCounterHandler.sendEmptyMessage(0);
	}
	private void stopTimeCounterThread(){
		mTimeCounterThreadFlag = false;
	}
	
	/**
	 * 切换语言
	 * @param locale
	 */
	private void switchLanguage(String language) {
		//设置应用语言类型
		Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        DisplayMetrics dm = resources.getDisplayMetrics();
       if (language.equals("en")) {
            config.locale = Locale.ENGLISH;
        } else {
        	 config.locale = Locale.SIMPLIFIED_CHINESE;
        }
        resources.updateConfiguration(config, dm);
        //保存设置语言的类型
        PreferenceUtil.commitString(PRELANGUAGE, language);
    }
	/**
	 * 语言选择的响应
	 * @param v
	 */
    @Override
	public void onClick(View v) {
		mDialog.dismiss();
		switch (v.getId()) {
			case R.id.select_english:
				if("en".equals(PreferenceUtil.getString(PRELANGUAGE, null))){
					return;
				}
				switchLanguage("en");
				break;
			case R.id.select_chinese:
				if("zh".equals(PreferenceUtil.getString(PRELANGUAGE, null))){
					return;
				}
				switchLanguage("zh");
				break;
			default:
				break;
		}
		//更新语言后，destroy当前页面，重新绘制
		finish();
		Intent it = new Intent(MainActivity.this, MainActivity.class);
	    startActivity(it);
	}
    /**
     * startActivityForResult  返回的回调
     * @param requestCode
     * @param resultCode
     * @param data
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
            	initBle(data);
            }
            break;
        }
    }
    /**
     * 显示任务
     * 
     * @param dur  set duration
     * @param ang  set angle
     * dur <=0 或 ang<=0 则认为 没有任务
     */
	private void displayTask(long dur, int ang){
		if(dur <=0 || ang <=0 ){
			taskTv.setText("");
		}
		int[] detail = secToDet(dur);
		taskTv.setText(String.format("%s   %d %s %02d : %02d : %02d   %d°",
				getResources().getString(R.string.task),
				detail[0],
				getResources().getString(R.string.days),
				detail[1],
				detail[2],
				detail[3],
				ang));
	}
	/**
	 * 显示正在执行的任务，剩余时间等信息
	 */
	private void displayTask(){
		
		runOnUiThread(new Runnable(){

			@Override
			public void run() {
				if(leftTime <= 0){
					timeLeftTv.setText("");
					taskTv.setText("");
					isStart = false;
					startBtn.setText(R.string.start);
				}else{
					int[] detail = secToDet(leftTime);
					timeLeftTv.setText(String.format("%s   %d %s %02d : %02d : %02d   %d°",
									getResources().getString(R.string.left),
									detail[0], 
									getResources().getString(R.string.days), 
									detail[1],
									detail[2],
									detail[3],
									(int)Math.floor(leftAngle))
							);
					isStart = true;
					startBtn.setText(R.string.stop);
				}
			}
		});
	}
}
