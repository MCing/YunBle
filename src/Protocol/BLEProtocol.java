package Protocol;

public class BLEProtocol {
	
	public final static  String TERMINATION = "T";  //停止指令
	public final static  String PAUSE = "P";		//暂停指令
	public final static  String CONTINUE = "C";		//继续指令
	public final static  String LEFTTIME = "L";		//从设备获取数据指令
	
	private int mAngle;
	private int mDuration;
	public BLEProtocol(){}
	
		public void setAngle(int angle){
			this.mAngle = angle;
		}
		public void setDuration(int duration){
			this.mDuration = duration;
		}
		/**
		 * 格式 ： Ax1Dx2;
		 * A、D位标识符，x1为设定角度（度）,x2为设定时长(秒)，";"为结束符时
		 * @return
		 */
		public String getCmd(){
			return "A"+this.mAngle+"D"+this.mDuration+";";
		}
}
