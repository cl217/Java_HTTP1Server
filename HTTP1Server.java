import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.io.IOException;
import java.lang.Integer;
import java.util.*;
import java.io.*;

public class HTTP1Server{

    public static ThreadManager threadManager;
    public static ServerSocket serverSocket;

    public static int testcase = 0;
    public static String serverIP = null;
    public static String serverPort = null;


    public static void main(String[]args){
        try{
            serverIP = InetAddress.getLocalHost().getHostAddress().trim();
        }catch(UnknownHostException e){
            e.printStackTrace();
        }
        serverPort = args[0];
        int portNo = Integer.parseInt(args[0]);

        //Starts threadManager;
	    threadManager = new ThreadManager();
    	threadManager.start();

        try{
            //Creates socket and listens for clients
            serverSocket = new ServerSocket(portNo);
            while( true ){
                Socket clientSocket = serverSocket.accept();
                ClientThread clientThread = threadManager.addClient(clientSocket);
                if(clientThread == null){
                    sendUnavailable(clientSocket);
                }else{
                    clientThread.start();
                    testcase++;
                }
            }
        }catch(IOException e){
            e.printStackTrace();
        }finally{
            //Ends threadManager and closes server socket
            
            try{
                threadManager.endThread();
                threadManager.join();
                serverSocket.close();
            }catch(IOException e){
                e.printStackTrace();
            }catch(InterruptedException e){
                e.printStackTrace();
            }
            
        }
    }

    /** Sends 503 error to client */ 
    public static void sendUnavailable(Socket clientSocket){
        try{
            BufferedOutputStream outToClient = new BufferedOutputStream(clientSocket.getOutputStream());
            String msg = "HTTP/1.0 503 Service Unavailable";
            outToClient.write(msg.getBytes());
            outToClient.flush();
            clientSocket.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

}




