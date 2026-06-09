package Ticket_Selling;

public class LiveDisplayTask implements Runnable {
    private TicketStore ticketStore;
    private Statistics statistics;

    public LiveDisplayTask(TicketStore ticketStore, Statistics statistics) {
        this.ticketStore = ticketStore;
        this.statistics = statistics;

    }

    @Override
    public void run() {
        while (statistics.getProcessed() < 80) {
            System.out.println();
            System.out.println("Live Display ");
            System.out.println("No. of left tikets : "+ ticketStore.getTicketsLeft());

            System.out.println("Processed requests: "+ statistics.getProcessed());
            try {
                Thread.sleep(50);
            }catch (InterruptedException e){
                return;
            }
        }
    }
}
