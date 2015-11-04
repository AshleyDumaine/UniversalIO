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
import java.util.List;

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
			/*Thread t = new Thread(new EEGServer());
			t.start();*/

			//connect to the EPOC server socket
			Socket clientSocket = new Socket("localhost", 4444);
			DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
			EEGServer.constructMaps();
			//get user param and use that to turn on the LED and send to server
			System.out.println("Enter EEG event to control LED: ");
			InputStream inFromServer = clientSocket.getInputStream();
			params = inFromUser.readLine();
			outToServer.writeBytes(params + '\n');
			String[] tokens = params.split(", ");
			System.out.println(EEGServer.getExpressivMap().toString());

			//fix this to terminate nicely, current setup causes stale lockfile
			if(ard.isConnected()) {
				while (true) {
					Input.EmotivEEGInput eegInput = Input.EmotivEEGInput.parseDelimitedFrom(clientSocket.getInputStream());
					System.out.println(eegInput.toString()); //debug
					for (String token : tokens) {
						//for expressiv events only
						if (EEGServer.getExpressivMap().containsKey(token)){
							Input.EmotivEEGInput.FacialExpression expression = eegInput.getExpression();
							if (expression.getExpression() == Input.EmotivEEGInput.FacialExpressionType.valueOf(token.toUpperCase())) {
								if (expression.getValue() > threshold && token == tokens[0]) {
									//turn on/off LED with same expression
									LEDSwitch(ard, !LED_ON);
								}
							}
						}
						//for affectiv events
						else if (EEGServer.getAffectivMap().containsKey(token)){
							List<Input.EmotivEEGInput.Mood> moods = eegInput.getAffectiveArrayList();
							for (Input.EmotivEEGInput.Mood mood : moods) {
								if (mood.getAffectiveAction() == Input.EmotivEEGInput.AffectiveActionType.valueOf(token.toUpperCase())) {
									if (mood.getExtent() > threshold && token == tokens[0]) {
										//turn on LED if above threshold
										LEDSwitch(ard, true);
									}
									if (mood.getExtent() < threshold && token == tokens[0]) {
										//turn off LED if below threshold
										LEDSwitch(ard, false);
									}
								}
							}
						}
						//for cognitiv events
						else if (EEGServer.getCogntivMap().containsKey(token)) {
							Input.EmotivEEGInput.CognitiveAction command = eegInput.getMentalCommand();
							if (command.getCognitiveAction() == Input.EmotivEEGInput.CognitiveActionType.valueOf(token.toUpperCase())) {
								if (command.getPower() > threshold && token == tokens[0]) {
									//turn on LED if above threshold
									LEDSwitch(ard, true);
								}
								else if (command.getPower() < threshold && token == tokens[0]) {
									//turn off LED if below threshold
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
		ard.digitalWrite(13, mode);
		LED_ON = mode;
	}
}
