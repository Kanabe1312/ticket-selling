package Ticket_Selling;

import java.util.concurrent.locks.ReentrantLock;

public class Statistics {
    private int processed;
    private final ReentrantLock lock = new ReentrantLock();

   public void incrementProcessed(){
    lock.lock();
    try{
      processed++;
    }finally {
      lock.unlock();
    }
   }

   public int getProcessed(){
       lock.lock();
       try{
           return processed;
       }finally {
           lock.unlock();
       }
   }


}
