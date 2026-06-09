package Ticket_Selling;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main {


    public static void main(String[] args) throws Exception {

        long startTime = System.currentTimeMillis();



        TicketStore ticketStore = new TicketStore(50);

        Statistics statistics = new Statistics();


        Thread liveDisplay = new Thread(new LiveDisplayTask(ticketStore, statistics));

        liveDisplay.setDaemon(true);
        liveDisplay.start();


        List<Customer> customers = new ArrayList<>();
        for (int i = 1; i <= 80; i++) {
            String name = String.format("Client-%03d", i);
            customers.add(new Customer(name));
        }


        ExecutorService executor = Executors.newFixedThreadPool(10);

        List<Future<Ticket>> futures = new ArrayList<>();


        for (Customer customer : customers) {
            Future<Ticket>future = executor.submit(new PurchaseTask(customer, ticketStore, statistics));
            futures.add(future);
        }

        int sold = 0;
        int rejected = 0;
        List<String>succesfulCustomers = new ArrayList<>();
        for(int i = 0; i < futures.size(); i++) {
            Ticket ticket = futures.get(i).get();

            if(ticket != null) {
                sold++;
                succesfulCustomers.add(customers.get(i).getName() + "->Ticket #" + ticket.getId());


            }else  {
                rejected++;
            }

        }

        long endTime = System.currentTimeMillis();

        executor.shutdown();
        System.out.println();
        System.out.println("=== Sumar ===");
        System.out.println("Sold: " + sold);
        System.out.println("Rejected: " + rejected);


        System.out.println();
        System.out.println("\nFirst 5 customers:");
        for(int i = 0; i < 5; i++) {
            System.out.println(
                    succesfulCustomers.get(i)
            );
        }


        System.out.println();
        System.out.println("Last 5 customers:");
        for(int i = succesfulCustomers.size() - 5; i < succesfulCustomers.size(); i++)
        {System.out.println(succesfulCustomers.get(i));
        }



        System.out.println();System.out.println("Duration: " + (endTime - startTime) + " ms");


    }
}
