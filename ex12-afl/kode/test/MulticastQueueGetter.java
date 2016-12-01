import multicastqueue.MulticastMessage;
import multicastqueue.MulticastQueue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

class MulticastQueueGetter<E extends Serializable> extends Thread
{
    private MulticastQueue<E> queue = null;
    private List<MulticastMessage> msgList = null;

    public static void wait(int seconds)
    {
        try 
        {
            Thread.currentThread().sleep(seconds*1000);
        }
        catch (Exception e) 
        {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public MulticastQueueGetter(MulticastQueue<E> queue)
    {
        this.queue = queue;
        msgList = new ArrayList<MulticastMessage>();
    }

    public MulticastMessage getNextMessage(int timeout) throws InterruptedException {
        final MulticastMessage[] res = new MulticastMessage[1];
        Thread getOnce = new Thread(){
            public void run()
            {
                res[0] = queue.get();
            }
        };
        getOnce.start();
        getOnce.join(timeout);

        if(getOnce.isAlive())
        {
            getOnce.interrupt();
        }
        return res[0];
    }

    public List<MulticastMessage> getAllMessages(int timeout) throws InterruptedException {
        this.start();

        join(timeout);
        if(isAlive())
        {
            interrupt();
            join(1000);
            //destroy();
        }
        return msgList;
    }

    public void run()
    {
        MulticastMessage msg;
        while ((msg = queue.get()) != null) 
        {
            msgList.add(msg);
        }
    }
}
