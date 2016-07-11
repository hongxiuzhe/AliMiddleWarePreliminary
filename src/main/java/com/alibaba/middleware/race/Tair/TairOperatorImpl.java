package com.alibaba.middleware.race.Tair;

import com.alibaba.middleware.race.RaceConfig;
import com.alibaba.middleware.race.jstorm.Count;
import com.esotericsoftware.minlog.Log;

import java.io.Serializable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.taobao.tair.DataEntry;
import com.taobao.tair.Result;
import com.taobao.tair.ResultCode;
import com.taobao.tair.impl.DefaultTairManager;


/**
 * ��дtair����Ҫ�ļ�Ⱥ��Ϣ����masterConfigServer/slaveConfigServer��ַ/
 * group ��namespace���Ƕ�������ʽ�ύ����ǰ��֪ѡ��
 */
public class TairOperatorImpl {
	private static final Logger LOG = LoggerFactory.getLogger(Count.class);
	public String mMasterConfigServer; // tair master
	public String mSlaveConfigServer;   // tair slave
	public String mGroupName;            // group name
	public int mNamespace;                 // namespace
	
	public  DefaultTairManager mTairManager;

    public TairOperatorImpl(String masterConfigServer,
                            String slaveConfigServer,
                            String groupName,
                            int namespace) {
    	this.mMasterConfigServer = masterConfigServer;
    	this.mSlaveConfigServer = slaveConfigServer;
    	this.mGroupName = groupName;
    	this.mNamespace = namespace;
        
    }
    
    public void initTair(){
    	// ����config server�б�  
        List<String> confServers = new ArrayList<String>();  
        confServers.add(mMasterConfigServer);   
        confServers.add(mSlaveConfigServer); // ��ѡ  
  
        // �����ͻ���ʵ��  
        mTairManager = new DefaultTairManager();  
        mTairManager.setConfigServerList(confServers);  
  
        // ��������  
        mTairManager.setGroupName(mGroupName);  
        // ��ʼ���ͻ���  
        mTairManager.init();         
       
    }

    public boolean write(Serializable key, Serializable value) {
    	// put key-value into tair  
       
        // ��һ��������namespace���ڶ�����key��������value�����ĸ��ǰ汾�����������Чʱ��  
    	int version = 0;
    	int expireTime = 1000 * 60 * 60;
        ResultCode result = mTairManager.put(mNamespace, key, value, version, expireTime);//versionΪ0���������İ汾��expireTimeΪ0������ʧЧ
        return result.isSuccess();       
    }

    public Object get(Serializable key) {
        // ��һ��������namespce���ڶ�����key  
        Result<DataEntry> result = mTairManager.get(mNamespace, key);  
        if (result.isSuccess()) {  
            DataEntry entry = result.getValue();  
            if (entry != null) {  
                // ���ݴ���  
                return entry.getValue();  
            } else {  
                // ���ݲ�����  
                return null;  
            }  
        } else {  
            // �쳣����  
            return null;
        }  
    }

    public boolean remove(Serializable key) {
        return false;
    }

    public void close(){
    	mTairManager.close();
    }
    
    public static void main(String[] args) {
    	TairOperatorImpl tairOperator = new TairOperatorImpl(RaceConfig.TairConfigServer, RaceConfig.TairSalveConfigServer,
                RaceConfig.TairGroup, RaceConfig.TairNamespace);
		tairOperator.initTair();
		String readKey = "platformTaobao_44761r8nkd_1467200520";
		String writeKey = "platformTaobao_44761r8nkd_1467200520";
		Double writeVal = 10.23;
		Double readVal = (Double) tairOperator.get(readKey);
		if(readVal==null)
			System.out.println(readKey + " not found");
		else
			System.out.println("key:" + readKey + " val:"+readVal);
		boolean ret = tairOperator.write(writeKey, writeVal);
		System.out.println("write to tair:" + ret);
    }
    
}
