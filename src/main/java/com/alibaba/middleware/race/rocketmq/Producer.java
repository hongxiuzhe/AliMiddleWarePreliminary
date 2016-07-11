
package com.alibaba.middleware.race.rocketmq;

import com.alibaba.middleware.race.RaceConfig;
import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.client.producer.DefaultMQProducer;
import com.alibaba.rocketmq.client.producer.SendCallback;
import com.alibaba.rocketmq.client.producer.SendResult;
import com.alibaba.rocketmq.common.message.Message;
import com.alibaba.middleware.race.model.*;
import com.alibaba.middleware.race.RaceUtils;
import com.alibaba.middleware.race.jstorm.Count;
import com.alibaba.middleware.race.jstorm.MetaSpout;
import com.alibaba.middleware.race.jstorm.MetaTuple;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Semaphore;

import org.apache.log4j.Logger;




/**
 * Producer��������Ϣ
 */
public class Producer {
	private static final Logger LOG = Logger.getLogger(Count.class);
    private static Random rand = new Random();
    private static int count = 300;
    
    public static MetaTuple produceMsg() throws InterruptedException {
    	 
    	 final String [] topics = new String[]{RaceConfig.MqTaobaoTradeTopic, RaceConfig.MqTmallTradeTopic};
         //final Semaphore semaphore = new Semaphore(0);
    	 MetaTuple msgToBroker = new MetaTuple();
    	 LOG.info("produce msg");
         for (int i = 0; i < count; i++) {
             try {
                  int platform = rand.nextInt(2);
                  OrderMessage orderMessage = ( platform == 0 ? OrderMessage.createTbaoMessage() : OrderMessage.createTmallMessage());
                 orderMessage.setCreateTime(System.currentTimeMillis());

                 byte [] body = RaceUtils.writeKryoObject(orderMessage);
                 
                 msgToBroker.addMyMsg(body, topics[platform]);
                 LOG.info(new Date().toString()+"---"+orderMessage);
                

                 //Send Pay message
                 PaymentMessage[] paymentMessages = PaymentMessage.createPayMentMsg(orderMessage);
                 double amount = 0;
                 for (PaymentMessage paymentMessage : paymentMessages) {
                     int retVal = Double.compare(paymentMessage.getPayAmount(), 0);
                     if (retVal < 0) {
                         throw new RuntimeException("price < 0 !!!!!!!!");
                     }

                     if (retVal > 0) {
                         amount += paymentMessage.getPayAmount();
                         msgToBroker.addMyMsg(RaceUtils.writeKryoObject(paymentMessage), RaceConfig.MqPayTopic);
                         LOG.info(new Date().toString()+"---"+paymentMessage);
                     }else {
                         //
                     }
                 }

                 if (Double.compare(amount, orderMessage.getTotalPrice()) != 0) {
                     throw new RuntimeException("totalprice is not equal.");
                 }


             } catch (Exception e) {
                 e.printStackTrace();
                 Thread.sleep(1000);
             }
         }
         //��һ��short��ʶ������ֹͣ��������
         byte [] zero = new  byte[]{0,0};
         msgToBroker.addMyMsg(zero, RaceConfig.MqTaobaoTradeTopic);
         msgToBroker.addMyMsg(zero, RaceConfig.MqTmallTradeTopic);
         msgToBroker.addMyMsg(zero, RaceConfig.MqPayTopic);

         return msgToBroker;
    }
    /**
     * ����һ��ģ��ѻ���Ϣ�ĳ������ɵ���Ϣģ�ͺ����Ǳ�������Ϣģ����һ���ģ�
     * ����ѡ�ֿ���������������������ݣ������µĲ��ԡ�
     * @param args
     * @throws MQClientException
     * @throws InterruptedException
     */
    public static void main(String[] args) throws MQClientException, InterruptedException {
        DefaultMQProducer producer = new DefaultMQProducer("please_rename_unique_group_name");

        //�ڱ��ش��broker��,�ǵ�ָ��nameServer�ĵ�ַ
        producer.setNamesrvAddr("192.168.122.66:9876");

        producer.start();

        final String [] topics = new String[]{RaceConfig.MqTaobaoTradeTopic, RaceConfig.MqTmallTradeTopic};
        final Semaphore semaphore = new Semaphore(0);

        for (int i = 0; i < count; i++) {
            try {
                final int platform = rand.nextInt(2);
                final OrderMessage orderMessage = ( platform == 0 ? OrderMessage.createTbaoMessage() : OrderMessage.createTmallMessage());
                orderMessage.setCreateTime(System.currentTimeMillis());

                byte [] body = RaceUtils.writeKryoObject(orderMessage);

                Message msgToBroker = new Message(topics[platform], body);

                producer.send(msgToBroker, new SendCallback() {
                    public void onSuccess(SendResult sendResult) {
                        System.out.println(orderMessage);
                        semaphore.release();
                    }
                    public void onException(Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });

                //Send Pay message
                PaymentMessage[] paymentMessages = PaymentMessage.createPayMentMsg(orderMessage);
                double amount = 0;
                for (final PaymentMessage paymentMessage : paymentMessages) {
                    int retVal = Double.compare(paymentMessage.getPayAmount(), 0);
                    if (retVal < 0) {
                        throw new RuntimeException("price < 0 !!!!!!!!");
                    }

                    if (retVal > 0) {
                        amount += paymentMessage.getPayAmount();
                        final Message messageToBroker = new Message(RaceConfig.MqPayTopic, RaceUtils.writeKryoObject(paymentMessage));
                        producer.send(messageToBroker, new SendCallback() {
                            public void onSuccess(SendResult sendResult) {
                                System.out.println(paymentMessage);
                            }
                            public void onException(Throwable throwable) {
                                throwable.printStackTrace();
                            }
                        });
                    }else {
                        //
                    }
                }

                if (Double.compare(amount, orderMessage.getTotalPrice()) != 0) {
                    throw new RuntimeException("totalprice is not equal.");
                }


            } catch (Exception e) {
                e.printStackTrace();
                Thread.sleep(1000);
            }
        }

        semaphore.acquire(count);

        //��һ��short��ʶ������ֹͣ��������
        byte [] zero = new  byte[]{0,0};
        Message endMsgTB = new Message(RaceConfig.MqTaobaoTradeTopic, zero);
        Message endMsgTM = new Message(RaceConfig.MqTmallTradeTopic, zero);
        Message endMsgPay = new Message(RaceConfig.MqPayTopic, zero);

        try {
            producer.send(endMsgTB);
            producer.send(endMsgTM);
            producer.send(endMsgPay);
        } catch (Exception e) {
            e.printStackTrace();
        }
        producer.shutdown();
    }
}
