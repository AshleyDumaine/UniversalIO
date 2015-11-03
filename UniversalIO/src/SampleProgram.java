/*
 * Uses the new API to control an LED
 */
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import arduino.Arduino;

public class SampleProgram {
	private static boolean LED_ON = false;
	public static void main(String[] args) throws UnknownHostException, IOException {
		double threshold = 0.4;
		Arduino ard = new Arduino("/dev/cu.usbserial-AD01VFL0"); //change this
		//connect the Arduino
		ard.connect();
		//set the pinmode
		ard.pinMode(13, 'o');
		String params;
		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));

		//run API_Main for the EPOC server socket
		try {
			Thread t = new Thread(new EEGServer());
			t.start();

			//connect to the EPOC server socket
			Socket clientSocket = new Socket("localhost", 4444);
			DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());

			//get user param and use that to turn on the LED and send to server
			System.out.println("Enter EEG event to control LED: ");
			InputStream inFromServer = clientSocket.getInputStream();
			params = inFromUser.readLine();
			outToServer.writeBytes(params + '\n');
			String[] tokens = params.split(", ");

			//fix this to terminate nicely, current setup causes stale lockfile
			if(ard.isConnected()) {
				int data = inFromServer.read();
				while (data != -1) {
					Input.EmotivEEGInput eegInput = Input.EmotivEEGInput.parseFrom(clientSocket.getInputStream());
					//System.out.println(eegInput.toString()); //debug
					for (String token : tokens) {
						//for expressiv events only
						if (EEGServer.getExpressivMap().containsKey(token)){
							Input.EmotivEEGInput.FacialExpression expression = eegInput.getExpression();
							if (expression.getExpression() == Input.EmotivEEGInput.FacialExpressionType.valueOf(token)) {
								if (expression.getValue() > threshold && token == tokens[0]) {
									//turn on/off LED
									LEDSwitch(ard, !LED_ON);
								}
							}
						}
						//for cognitiv events
						else if (EEGServer.getCogntivMap().containsKey(token)) {
							Input.EmotivEEGInput.CognitiveAction command = eegInput.getMentalCommand();
							if (command.getCognitiveAction() == Input.EmotivEEGInput.CognitiveActionType.valueOf(token)) {
								if (command.getPower() > threshold && token == tokens[0]) {
									//turn on LED
									LEDSwitch(ard, true);
								}
								else if (command.getPower() < threshold && token == tokens[1]) {
									//turn off LED
									LEDSwitch(ard, false);
								}
							}
						}
						else {
							System.out.println("Received bad input, enter a valid EEG event.");
							System.exit(0);
						}
					}
				}
				data = inFromServer.read();
			}
			//close all resources, disconnect Arduino
			clientSocket.close();
			inFromUser.close();
			inFromServer.close();
			outToServer.close();
			//ard.disconnect(); //makes no sense to include this right now
		} catch (SocketException e) {
			System.out.println("Could not start EPOC data server socket.");
			e.printStackTrace();
		}
	}

	public static void LEDSwitch(Arduino ard, boolean mode) {
		System.out.println("Turning on LED...");
		ard.digitalWrite(13, mode);
		LED_ON = mode;
	}
}
