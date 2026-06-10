package Ticket_Selling;

import java.util.concurrent.locks.ReentrantLock;

public class TicketStore {
    private int ticketsLeft;
    private int nextTicketId;
    private final ReentrantLock lock = new ReentrantLock();

    public TicketStore(int ticketsLeft) {
        this.ticketsLeft = ticketsLeft;
        this.nextTicketId = 1;
    }
   public int getTicketsLeft() {
        lock.lock();
        try {
            return ticketsLeft;
        }finally {
            lock.unlock();
        }
   }

    public Ticket buyTicket(){
        lock.lock();
        try {
            if(ticketsLeft <= 0){
                return null;
            }
            Ticket ticket = new Ticket(nextTicketId);
            nextTicketId++;
            ticketsLeft--;
            return ticket;
        }finally {
            lock.unlock();
        }
    }
}
