package Ticket_Selling;

import java.util.Random;
import java.util.concurrent.Callable;

public class PurchaseTask implements Callable<Ticket> {

    private Customer customer;
    private TicketStore ticketStore;
    private Statistics statistics;
    public PurchaseTask(Customer customer, TicketStore ticketStore,Statistics statistics) {
        this.customer = customer;
        this.ticketStore = ticketStore;
        this.statistics = statistics;
    }
    @Override
    public Ticket call() throws Exception {
        Random random = new Random();

        Thread.sleep(random.nextInt(101) + 50);

        Ticket ticket = ticketStore.buyTicket();

        statistics.incrementProcessed();

        return ticket;

    }
}
