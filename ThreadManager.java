import java.net.Socket;
import java.util.*;

public class ThreadManager extends Thread{
    
    int maxActive = 10;
    int maxIdle = 5;
    boolean end = false;

    ArrayList<ClientThread> threadList = new ArrayList<ClientThread>(); 
	ArrayList<ClientThread> idleThreads = new ArrayList<ClientThread>();
	Queue<ClientThread> removeQ = new LinkedList<ClientThread>();

    //Main thread method
	public void run(){
		while(true){
			while(!removeQ.isEmpty()){
				ClientThread thread = removeQ.peek();
				try{
					thread.join();
				}catch(InterruptedException e){
				}
				removeQ.remove();
			}
			if( idleThreads.size() < maxIdle && (threadList.size()+idleThreads.size()) < maxActive ){
				createIdleThreads();
            }
            if( end == true ){
                return;
            }
		}
	}

    /** creates available ClientThreads */
	public synchronized void createIdleThreads(){
		int create = maxIdle - idleThreads.size();
		if( (maxActive-threadList.size()) < create ){
			create = maxActive-threadList.size();
		}
		for( int i = 0; i < create; i++ ){
			idleThreads.add(new ClientThread());
		}
	}

    /** returns a ClientThread to be used */
    public synchronized ClientThread addClient(Socket clientSocket){
        if( threadList.size() >= maxActive ){
            return null;
        }
        if( idleThreads.size() > 0 ){
        	ClientThread clientThread = idleThreads.get(0);
        	idleThreads.remove(0);
        	clientThread.setSocket(clientSocket);
        	return clientThread;
        }
        ClientThread clientThread = new ClientThread(clientSocket);
        threadList.add(clientThread);
        return clientThread;
    }

    /** Adds ClientThread to queue to be joined and removed*/
    public synchronized void removeClient(ClientThread client){
    	threadList.remove(client);
    	removeQ.add(client);
    }

    /**Notifies thread to end */
    public synchronized void endThread(){
        end = true;
    }
        
}
