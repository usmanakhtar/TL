package art.experiments.wifi.data.processor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

public class WifiUtils {

	public static List<String> getLines(String inputFile) {
		//System.out.println(inputFile);
		Pattern pattern = Pattern.compile("\\n");	
		Scanner scan = null;
		try {
			scan = new Scanner(new File(inputFile));	
			scan.useDelimiter(pattern);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		List<String> lines = new ArrayList<String>();
		while (scan.hasNext()){
			String text = scan.next();	
			if (!text.isEmpty()) {
			 lines.add(text);
			}
		}
		
		return lines;
	}
}
