package Ticket_Selling;

public class TicketStore {
    private int ticketsLeft;
    private int nextTicketId;

    public TicketStore(int ticketsLeft) {
        this.ticketsLeft = ticketsLeft;
        this.nextTicketId = 1;
    }
    public synchronized int getTicketsLeft() {
        return ticketsLeft;
    }

    public synchronized Ticket buyTicket() {

       if (ticketsLeft <= 0) {
           return null;
       }
       Ticket ticket = new Ticket(nextTicketId);
       nextTicketId++;
       ticketsLeft--;


        return ticket;

    }
}
