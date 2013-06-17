package tl.experiments;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


public class StaticUtils {
	
	public static void printMap(Map<String, List<String>> mymap) {
		//System.out.println("Map: ");
		for (Entry<String, List<String>> entry : mymap.entrySet()) {
			System.out.print(entry.getKey() + " --> " );
			List<String> values =  entry.getValue();
			for (int i=0; i < values.size(); i++) {
				if (i > 0) {
					System.out.print(", ");
				}
				System.out.print(values.get(i) );
			}
			System.out.println();
		}
		System.out.println();
		
	}
	
	public static void printMapNewlines(Map<String, List<String>> mymap) {
		//System.out.println("Map: ");
		for (Entry<String, List<String>> entry : mymap.entrySet()) {
			System.out.print(entry.getKey() + " --> " );
			List<String> values =  entry.getValue();
			for (int i=0; i < values.size(); i++) {		
				if (i == 0) {
					System.out.println();
				}
				System.out.println("\t" + values.get(i) );
			}
			System.out.println();
		}
		System.out.println();
		
	}
	
	
	public static void printList(List<String> l) {
		//System.out.println("List: ");
		for (String s : l) {
			System.out.println(s);
		}
		System.out.println();
	}
	
	public static void stop() {
		System.out.println("Terminating because of user stop.");
		System.exit(0);
	}

}