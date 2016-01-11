package com.bigdata.sync;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Random;
import java.util.Set;

import com.bigdata.DataClass.ConfData;
import com.bigdata.DataClass.JNData;
import com.bigdata.DataClass.JamSignalData;
import com.bigdata.DataClass.NonJamRTSData;
import com.bigdata.DataClass.Package;
import com.bigdata.DataClass.RFNodeData;
import com.cetc.remote.Agent;
import com.cetc.remote.AliasServer;

@SuppressWarnings("unused")
public class SlaveNode implements Slave {
	
	LinkedList<Package> packageList = new LinkedList<Package>();
	Set<Short> isDeletedSet = new HashSet<Short>();
	public static final int ARRAYLEN = ConfData.readProperties().arrayLen;
	
	public void save(short default1) {
		
		System.out.println("((((((((((((((((((((((SAVE))))))))))))))))))))))))");
		
		synchronized(this) {
			
			isDeletedSet.add(default1);
			
			ListIterator<Package> lit = (ListIterator<Package>) packageList.iterator();
			int tempCount;
			int len;
			String key = null;
			Package tempPackage = null;
			
			System.out.println("save Default1--->" + default1);
			
			if (!packageList.isEmpty()) {    		// find the target data
				while (lit.hasNext()) {
					tempPackage = lit.next();
					if (default1 == tempPackage.getDefault1()) {
						packageList.remove(tempPackage);  				// delete data in the linked list
						break;
					}
				}
			}
			
			// send data to Kafka
			if (tempPackage != null) {
				JNData[] tempDataArray = tempPackage.getDataArray();
				tempCount = tempPackage.getCount();
				len = tempDataArray.length;
				for (int i = 0; i < len; i++) {
					if (tempDataArray[i] != null) {
						key = (default1 + "") + "|" + (tempCount + "");
						if (key != null) {
							System.out.println("####send to Kafka: Key--->" + key);
							System.out.println("####send to Kafka: Default1--->" + tempDataArray[i].getDefault1());
							KafkaProducer.producer(key, tempDataArray[i]);
						}
					}
				}
			}			
		}
	}
	
	public synchronized void processData(JNData inData) {
		ListIterator<Package> lit = (ListIterator<Package>) packageList.iterator();
		short tempDefault1;
		int tempCount;
		boolean signAdd = false;
		int tempIndex;
		
		System.out.println("<<<<<<<<<<<<<packageList.size() = " + packageList.size());
		
		if (isDeletedSet.isEmpty() || !isDeletedSet.contains(inData.getDefault1())) {    // make sure that this default1 has not been deleted before
			
			System.out.println("<<<<<<<<<<<<<isDeletedSet.size() = " + isDeletedSet.size());
			
			if (packageList.isEmpty()) {			// if the packageList is empty, add inData to packageList
				Package tempPackage = new Package();
				JNData[] tempDataArray = new JNData[ARRAYLEN];
				tempDataArray = tempPackage.getDataArray();
				tempPackage.setDefault1(inData.getDefault1());
				tempPackage.setCount(1);
				tempDataArray[0] = new JNData();
				tempDataArray[0] = inData;
				tempPackage.setDataArray(tempDataArray);
				packageList.add(tempPackage);
				signAdd = true;
				first(inData.getDefault1());			// send first message to control node
				if (inData.getDefault2() == 1) {			// check if it triggers the sending condition, if so, send last message
					last(inData.getDefault1());
				}
			}
			else {			// if the packageList is not empty, traverse packageList and find the inserting position
				while (lit.hasNext()) {
					Package tempPackage = new Package();
					JNData[] tempDataArray = new JNData[ARRAYLEN];
					tempPackage = lit.next();
					tempDataArray = tempPackage.getDataArray();
					tempDefault1 = tempPackage.getDefault1();
					if (tempDefault1 == inData.getDefault1()) {		// find the inserting position before traversal is over
						tempIndex = packageList.indexOf(tempPackage);		// record the inserting position
						tempCount = tempPackage.getCount();
						tempDataArray[tempCount] = inData;
						tempCount++;
						tempPackage.setCount(tempCount);
						tempPackage.setDataArray(tempDataArray);
						packageList.set(tempIndex, tempPackage);			// insert inData
						signAdd = true;
						if (tempCount == inData.getDefault2()) {			// check if it triggers the sending condition, if so, send last message
							last(inData.getDefault1());
						}
						break;
					}
				}
				if (signAdd == false) {			// if there is no position to insert inData, insert it to the tail of packageList
					Package tempPackage = new Package();
					JNData[] tempDataArray = new JNData[ARRAYLEN];
					tempDataArray = tempPackage.getDataArray();
					tempPackage.setDefault1(inData.getDefault1());
					tempPackage.setCount(1);
					tempDataArray[0] = inData;
					tempPackage.setDataArray(tempDataArray);
					packageList.add(tempPackage);
					signAdd = true;
					first(inData.getDefault1());    // send first message to control node
					if (inData.getDefault2() == 1) {    // check if it triggers the sending condition, if so, send last message
						last(inData.getDefault1());
					}
				}
			}
		}
	}
	
	public void first(short default1) {				// send first message to control node
		System.out.println("=======first default1 = " + default1);
		MasterServer.INSTANCE.remote("radar.master").data().processMsgFirst(default1);
	}
	
	public void last(short default1) {			// send last message to control node
		System.out.println("*******last default1 = " + default1);
		MasterServer.INSTANCE.remote("radar.master").data().processMsgLast(default1);
	}

	public void test() throws InterruptedException {
		new Thread(new Runnable() {
			@Override
			public void run() {
				Random r = new Random();
				long PackageNO = Math.abs(r.nextLong());												/*数据包编号*/
				int FrameNo = Math.abs(r.nextInt());														/*帧号*/
				int TBeamID;																								/*发射波位编号*/
				double time = Math.abs(r.nextDouble());												/*发射波位开始扫描时间*/
				int Rid;																										/*接收节点ID*/
				int Tid;																										/*发射节点ID*/
				int RBeamNum = Math.abs(r.nextInt());													/*接收波束数量*/
				int PulseNum = Math.abs(r.nextInt() % 1001);   										/*脉冲数量 1~1000*/
				short dataType = (short) (Math.abs(r.nextInt() % 6) + 1);						/*数据类型 1-6*/
				short ajFlag;																								/*抗干扰标志 0 or 1*/
				long totalSize = 100;																					/*回波数据总长度*/
				short[] waveData = {1,3,5,7,9,2,4,6,8,0};													/*目标回波数据 保存20个通道、30个脉冲的数据*/
				short Default1;																							/*预留字段1: increasing send number*/
				int Default2;																								/*预留字段2: 7 packages at most*/
				
				for (short i = 1; i <= 20; i++) {
//					int total = r.nextInt(7) + 1;
					int total = 7;
					for (int j = 1; j <= total; j++) {
						int millis = r.nextInt(200);
						try {
							Thread.sleep((long)millis);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						
						System.out.printf("i = %s, j = %s\n", i, j);
						ajFlag = (short) Math.abs(r.nextInt() % 2);
						Rid = j;
						Tid = i + 1000;
						TBeamID  = Math.abs(r.nextInt());
						
						RFNodeData rData = new RFNodeData();
						rData.ajFlag = ajFlag;
						rData.Rid = Rid;
						rData.Tid = Tid;
						rData.TBeamID = TBeamID;
						rData.Default1 = i;
						rData.Default2 = total;
						
						processData(SlaveNode.preProcessData(rData));
					}
				}
			}
		}).start();
	}
	
	public static JNData preProcessData(RFNodeData preData){
	    Random r = new Random();
	    JNData jnData = new JNData();
	    JamSignalData jam = new JamSignalData();
	    NonJamRTSData nonJam = new NonJamRTSData();
	    jam = process1(preData);
	    nonJam = process2(preData);
//	    int flag = Math.abs(r.nextInt() % 3);    // produce random number: 0,1,2
	    int flag = 2;
	    if (flag == 0) {						// jnData has only jam
	    	jnData.jamData = jam;
	        jnData.hasJam = true;
	        jnData.nonJamData = null;
	        jnData.hasNonJam = false;
	        jnData.setDefault1(jam.Default1);
	        jnData.setDefault2(jam.Default2);
	    } else if (flag == 1) {			// jnData has only nonJam
	    	jnData.jamData = null;
	        jnData.hasJam = false;
	        jnData.nonJamData = nonJam;
	        jnData.hasNonJam = true;
	        jnData.setDefault1(jam.Default1);
	        jnData.setDefault2(jam.Default2);
	    } else {   								// jnData has both of jam and nonJam
	    	jnData.jamData = jam;
	        jnData.hasJam = true;
	        jnData.nonJamData = nonJam;
	        jnData.hasNonJam = true;
	        jnData.setDefault1(jam.Default1);
	        jnData.setDefault2(jam.Default2);
	    }
	    
	    return jnData;
	}
	
	public static JamSignalData process1 (RFNodeData data) {
	    JamSignalData jam = new JamSignalData();
	    jam.ajFlag = data.ajFlag;
	    jam.Rid = data.Rid;
	    jam.Tid = data.Tid;
	    jam.TBeamID = data.TBeamID;
	    jam.Default1 = data.Default1;
	    jam.Default2 = data.Default2;
	    return jam;
	}
	
	public static NonJamRTSData process2 (RFNodeData data) {
		NonJamRTSData nonJam = new NonJamRTSData();
		nonJam.ajFlag = data.ajFlag;
		nonJam.Rid = data.Rid;
		nonJam.Tid = data.Tid;
		nonJam.TBeamID = data.TBeamID;
		nonJam.Default1 = data.Default1;
		nonJam.Default2 = data.Default2;
	    return nonJam;
	}
	
}


class SlaveAgent extends Agent<Slave, SlaveNode> {
	public static final SlaveNode server = new SlaveNode();
	public static final SlaveAgent INSTANCE = new SlaveAgent();
	private SlaveAgent() {
		super(Slave.class, SlaveNode.class, server);
	}
}

class SlaveServer extends AliasServer<Slave> {
	public static final SlaveServer INSTANCE = new SlaveServer();
	private SlaveServer() {
		super(Slave.class);
	}
}		

