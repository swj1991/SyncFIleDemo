package FileSync;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import filesync.CopyBlockInstruction;
import filesync.Instruction;
import filesync.NewBlockInstruction;
import filesync.SynchronisedFile;

public class syncclient {
	private WatchService watcher=null;
    private Map<WatchKey,Path> keys=null;
    private boolean recursive=false;
    private boolean trace = false;
    public static String k1 ="";
    public static String k2 ="";
    private static Socket client;
	private static DataInputStream input;
	private static DataOutputStream output;
	private static SynchronisedFile fromfile;
	private static int size = 1024;
	private static int port = 4444;
	private static String path = "";
    private static String hostname = "localhost";
    private static int numberoffiles=0;
    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }

    /**
     * Register the given directory with the WatchService
     */
    public void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                System.out.format("register: %s\n", dir);
            } else {
                if (!dir.equals(prev)) {
                    System.out.format("update: %s -> %s\n", prev, dir);
                }
            }
        }
        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    public void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException
            {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Creates a WatchService and registers the given directory
     */
    syncclient(Path dir, boolean recursive) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey,Path>();
        this.recursive = recursive;
        this.port=port;
        this.path = dir.toString();
        this.hostname=hostname;
        if (recursive) {
            System.out.format("Scanning %s ...\n", dir);
            registerAll(dir);
            System.out.println("Done.");
        } else {
            register(dir);
        }

        // enable trace after initial registration
        this.trace = true;
    }
    public syncclient(int portnumber, String hostname){
    	   this.port=portnumber;
    	   this.hostname =hostname;
    }

    /**
     * Process all events for keys queued to the watcher
     * @throws InterruptedException 
     */
    public void processEvents() throws InterruptedException {
        for (;;) {
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event: key.pollEvents()) {
                WatchEvent.Kind kind = event.kind();

                // TBD - provide example of how OVERFLOW event is handled
                if (kind == OVERFLOW) {
                    continue;
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);
                k1 = k1+event.kind().name()+",";
                k2 = k2+child.toString()+",";
 
                //Thread.sleep(20);
           
               
                if (recursive && (kind == ENTRY_CREATE)) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            registerAll(child);
                        }
                    } catch (IOException x) {
          
                    }
                }
                
                // if directory is created, and watching recursively, then
                // register it and its sub-directories
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }
	 private static void getStreams() throws IOException{
		 output = new DataOutputStream(client.getOutputStream());
		 output.flush();
		 input = new DataInputStream(client.getInputStream());	 
	 }
	 private static void setFile(String path,int size) throws IOException{		 
	      fromfile = new SynchronisedFile(path,size);
	 }
	 private static void connectToServer() throws UnknownHostException, IOException, SocketException{
		 client = new Socket(hostname, port);	 
	 }
	 private static void sendData(String data) throws IOException{
		     output.writeUTF(data);
		     output.flush();
	 }
	 private static void closeConntction() throws IOException{
		      output.close();
		      input.close();
		      client.close();
	 }
	 private static String[] getFiles(String p){
		 File folder = new File(p);
		 File[] listOfFiles = folder.listFiles();
		 ArrayList<String> files = new ArrayList<String>();
		            for(int i=0;i<listOfFiles.length;i++){
	            	if(!listOfFiles[i].getName().contains(".tmp") && !listOfFiles[i].getName().contains("~$")){
		            		files.add(listOfFiles[i].getName());
		            	}
		            }
		            String[] currentfiles = new String[files.size()];
		           for(int i=0;i<files.size();i++){
		        	   currentfiles[i]=files.get(i);
		           }
		              return currentfiles;
	 }	 
	private static void checkthestateforfiles(SynchronisedFile file) throws IOException{
		    Instruction inst;
		    Instruction instfornewcopy;
		    String feedback= "please wait...";
		    while((inst=file.NextInstruction())!=null){
		    	   String msg = inst.ToJSON();
		    	   sendData(msg);
		    	    feedback= input.readUTF();
		    	   if(feedback.contains("upgrade block")){
		    			Instruction upgraded=new NewBlockInstruction((CopyBlockInstruction)inst);
						String msg2 = upgraded.ToJSON();
						sendData(msg2);
		    	   }else if(feedback.contains("Update successfully!")){
		    		     System.out.println(feedback);
		    		     break;
		    	   }else if(feedback.contains("nothing change!")){
		    		     break;
		    	   }else if(feedback.contains("Empty file!")){
		    		    // System.out.println(feedback);
		    		     break;
		    	   }		    	
		    }
		   
	}
    public static void main(String[] args) throws IOException, InterruptedException {    	        
    	          String path = "fromFile";
    	          Path dir = Paths.get(path);
    	          String hostname = "localhost";
    	          int portnumber = 4444;
    	          syncclient s = new syncclient(portnumber, hostname);
    	              connectToServer();
    	        	  getStreams();
    	        	  String[] allfiles=getFiles(path);
    	        	  System.out.println(input.readUTF()); 
    	        	   if(allfiles.length!=0){
    	        		  for(int i=0; i<allfiles.length; i++){
    	        			  sendData("CHECK"+"\\"+allfiles[i]);
    	        			  String existornot = input.readUTF();
    	        			  System.out.println(existornot);
    	        			  if(existornot.equals("false")){
    	        				  sendData("CREATE"+"\\"+allfiles[i]);
        	        			  System.out.println(input.readUTF());  	        			
    	        			  } 	        			  
    	        		  }
    	        	  }
    	        	 Thread checknumberoffiles = new Thread(new Runnable(){
						@Override
						public void run() {
							  for(;;){
								  
								  try {
										Thread.sleep(2000);
									} catch (InterruptedException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								  if( k1.equals("")){    						     
		    						}else{
		       							String[] ActionArray = k1.split(",");
		        						String[] FileArray = k2.split(",");       						
		        					     for(int j=0; j<ActionArray.length;j++){
		        					    	 String change =  ActionArray[j]+":"+FileArray[j];							
		         							     System.err.println(change);
		         								 if(ActionArray[j].contains("DELETE") && !FileArray[j].contains(".tmp") && !FileArray[j].contains(".~$") && !FileArray[j].contains("DS_Store")){
		         									 try {
		     											 sendData(change);
		     											 System.out.println(input.readUTF());
		     										} catch (IOException e) {
		     											// TODO Auto-generated catch block
		     											e.printStackTrace();
		     										}
		         								}else if(ActionArray[j].contains("CREATE") && !FileArray[j].contains(".tmp") && !FileArray[j].contains(".~$") && !FileArray[j].contains("DS_Store")){
		         									//System.out.println(change);  									
		         									try {
		     											sendData(change);
		     											System.out.println(input.readUTF());
		     										} catch (IOException e) {
		     											// TODO Auto-generated catch block
		     											e.printStackTrace();
		     										}
		         								}					 
		        					     }   							
		    							k1="";
		    							k2="";
		    						} 
								  String[] Allfiles = getFiles(path);								 
								  numberoffiles = getFiles(path).length;
								  SynchronisedFile[] currentfiles = new SynchronisedFile[numberoffiles];
								  if(numberoffiles>0){
									  for(int i= 0; i<numberoffiles; i++){
										  try {
											currentfiles[i] = (new SynchronisedFile(path+"\\"+Allfiles[i]));
										    currentfiles[i].CheckFileState();
										} catch (IOException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										} catch (InterruptedException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
										  try {
	                                         sendData("MODIFY"+"\\"+Allfiles[i]);
											checkthestateforfiles(currentfiles[i]);
											
										} catch (IOException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}										  
									  }
								  }								
							  }							
						}   	        		 
    	        	 });
    	        	 checknumberoffiles.setDaemon(true);
    	        	 checknumberoffiles.start();
      			     syncclient check1 = new syncclient(dir,false);    			     
    			     check1.processEvents();
    }
}
