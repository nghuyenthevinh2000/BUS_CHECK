package rfid.rfid_input;

import com.fazecast.jSerialComm.*;

import rfid.JSONHandler;
import rfid.LogHandler;
import rfid.JSONHandler.JSONField;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;

public class PortInputHandler extends InputHandler {
   private byte[] output = null;
   private SerialPort[] comPortList;
   private SerialPort comPort;
   private JSONHandler config_handler;

   private PortInputHandler() {
   }

   public static PortInputHandler getInputHandler() {
      PortInputHandler inputHandler = new PortInputHandler();
      inputHandler.config_handler = JSONHandler.getJSONHandler().getJSONObjectFromFile(JSONHandler.CONFIGS_DIR);
      int retry_time = Integer.parseInt(inputHandler.config_handler.getField(JSONField.DEVICE_CONNECT_RETRY_TIME));
      // Exception 1: Cannot connect to device
      while (true) {
         inputHandler.comPortList = SerialPort.getCommPorts();
         if (inputHandler.comPortList.length > 0)
            break;

         LogHandler.log_txt(PortInputHandler.class.getName(),Level.INFO, "Fail to connect device");
         try {
            Thread.sleep(Long.parseLong(inputHandler.config_handler.getField(JSONField.DEVICE_CONNECT_FAIL_TIME_OUT_MS)));
         } catch (InterruptedException e) {
            LogHandler.log_err(PortInputHandler.class.getName(), e);
            Thread.currentThread().interrupt();
         }

         //if has retried retry_time, exit program
         retry_time--;
         if (retry_time == 0)
            System.exit(0);
      }

      inputHandler.comPort = inputHandler.comPortList[0];
      return inputHandler;
   }

   public boolean openPort() {
      // guaranted to have device connected
      comPort.openPort();
      return comPort.isOpen();
   }

   public void setPortConfig() {
      comPort.setBaudRate(38400);
      comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 1000, 1000);
   }

   public void closePort() {
      comPort.closePort();
   }

   public String getRFIDInput() {
      setPortConfig();
      openPort();

      //check if port is open
      if(!comPort.isOpen()) {
         LogHandler.log_txt(PortInputHandler.class.getName(),Level.SEVERE, "Port is occupied, now exit");
         System.exit(0);
      }

      //listen for data and handle data
      comPort.addDataListener(new SerialPortDataListener() {
         @Override
         public int getListeningEvents() {
            return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
         }

         @Override
         public void serialEvent(SerialPortEvent event) {
            if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE)
               return;
            int availableByte = comPort.bytesAvailable();
            byte[] newData = new byte[availableByte];
            int readByte = comPort.readBytes(newData, availableByte);
            output = newData;

            //write to LOG_OFFLINE for debug purpose only
            String log_content = String.format("Total bytes = %s, Bytes failed to read = %s", availableByte, availableByte - readByte);
            LogHandler.log_txt(PortInputHandler.class.getName(), Level.INFO, log_content);
         }
      });
      closePort();
      if(output == null) return null;

      //output
      String result = "";
      for(byte i : output){
         result += i;
      }

      //nếu đúng 18 bytes, trả về luôn
      if(output.length == 18) 
         return result;
      //nếu nhỏ hơn 18 bytes, không đủ để xác thực, bỏ 
      else if(output.length < 18)
         return null;

      output = null;
      //lớn hơn 18 bytes, cắt chuỗi, trả về
      return processBytes(result);
   }

   private String processBytes(String bytes){
      //define new Regex Pattern
      Pattern p = Pattern.compile("170-180");
      Matcher m = p.matcher(bytes);
      //find two bounds
      int posi[] = new int[2];
      for(int i=0;i<2;i++){
         if(m.find()) posi[i] = m.start();
      }
      //Cut string
      return bytes.substring(posi[0], posi[1]);
   }
}
