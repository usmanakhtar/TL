package art.experiments;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import org.apache.mahout.fpm.pfpgrowth.FPGrowthDriver;
import art.experiments.wifi.data.processor.EventInfo;
import art.experiments.wifi.data.processor.Sensor;
import art.experiments.wifi.data.processor.WifiAligner;
import art.experiments.wifi.data.processor.WifiUtils;
import art.framework.example.parser.AbstractPredicateWriter;
import art.framework.utils.Constants;
import art.framework.utils.Utils;
import svmjava.*;


public class WifiExperimentRunner {

	public static enum FILE_TYPE { // used for folder names
		TEST(0), TRAIN(1); // test, or train data

		private final int index;

		FILE_TYPE(int index) {
			this.index = index;
		}

		public int index() {
			return index;
		}

		public static int length() {
			return values().length;
		}
	}

	public static enum TRANSFER_TYPE { // used for folder names
		TRANSFER(0), NOTRANSFER(1); // transfer, or no-transfer

		private final int index;

		TRANSFER_TYPE(int index) {
			this.index = index;
		}

		public int index() {
			return index;
		}

		public static int length() {
			return values().length;
		}
	}

	public static enum FEATURE_TYPE { // used for folder names
		OF(0), HF(1); // Our Features, or Her/Handcrafted Features

		private final int index;

		FEATURE_TYPE(int index) {
			this.index = index;
		}

		public int index() {
			return index;
		}

		public static int length() {
			return values().length;
		}
	}

	// ==================== constants ====================

	public final String classMapFile = "../arf.experiments.wifi/housedata/input/classMap.txt";
	public static final String ROOT_DIR = "../arf.experiments.wifi/housedata/";
	private Random rand = new Random(System.currentTimeMillis());

	private String[] houses;
	private int numberHouses;
	private double results[][][][]; // for evaluation 	
	private double[] maxDaysPlotPerHouse;

	/*
	 * specifies whether a class label should be considered when aggregating
	 * values for predicate types, that will later be used to infer value ranges
	 * (associated either just with predicate type or with both predicate type
	 * and class).
	 */
	public final boolean USE_CLASS = true;
	
	/*
	 * How many times you grab some random data points of amount noDays
	 * (see nodaysArray) and construct leave-one-out train+test sets from it
	 */
	private int NO_DATA_INSTANCES = 2;

	/*
	 * Array with number (amount) of days considered for train/test data train
	 * set will have the size of the number minus one (one left out for testing)
	 * 
	 */
	private int[] noDaysArray = { 2, 3,6,11,21}; 

	/*
	 * if true, ranges will be added for variable values
	 */
	private boolean withRanges = false;

	private  String conf = "0.5"; // confidence cut off used to extract rules with FP growth for non-transfer model (target house)
	private  String confTr = "0.3"; // confidence cut off used to extract rules with FP growth for transfer model (source houses)


	public void set_NO_DATA_INSTANCES(int nO_DATA_INSTANCES) {
		NO_DATA_INSTANCES = nO_DATA_INSTANCES;
	}

	public void setNoDaysArray(int[] noDaysArray) {
		this.noDaysArray = noDaysArray;
	}

	public void setConf(String conf) {
		this.conf = conf;
	}

	public void setConfTr(String confTr) {
		this.confTr = confTr;
	}
	
	public void setWithRanges(boolean withRanges) {
		this.withRanges = withRanges;
	}
	
	public String toString() {
		StringBuilder s= new StringBuilder();
		//s.append("------ WER settings : ------ ");
		s.append("NO_DATA_INSTANCES: " + NO_DATA_INSTANCES + "\n");
		s.append("noDaysArray: " + Arrays.toString(noDaysArray) + "\n");
		s.append("withRanges (boolean): " + withRanges + "\n");
		s.append("conf: " + conf + "\n");
		s.append("confTr: " + confTr + "\n");
		s.append("USE_CLASS (boolean): " + USE_CLASS);
		//s.append("---------------------------- ");
		return s.toString();
	}

	public void turnLoggingOff() {
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
	}
	
	public WifiExperimentRunner() {
		// empty constructor
	}

	public static void main(String[] args) {
		WifiExperimentRunner wer = new WifiExperimentRunner();
		wer.turnLoggingOff();
		wer.run();
	}

	public void run() {
		runTransferAlgorithm();
		runEvaluation();
	}

	public void runEvaluation() {
		evaluateUsingSVM();
		evaluationResultsToMatlabPerHouse();
	}

	

	public void init() {
		numberHouses = Utils.getDirectorySize(ROOT_DIR + "input/" + FEATURE_TYPE.HF);
		houses = new String[numberHouses];
		maxDaysPlotPerHouse = new double[numberHouses];
		for (int i = 0; i < numberHouses; i++) {
			houses[i] = WifiUtils.intToString(i);
		}
		// make new output directory
		String outputDirNameAllHouses = ROOT_DIR + "output/";
		Utils.resetDirectory(outputDirNameAllHouses);

		
	}

	public Map<String, List<String>> makeHousesMap() {
		Map<String, List<String>> housesMap = new HashMap<String, List<String>>();

		for (int i = 0; i < numberHouses; i++) {
			List<String> otherhouses = new ArrayList<String>();
			for (int j = 0; j < numberHouses; j++) {
				if (j != i) {
					otherhouses.add(WifiUtils.intToString(j));
				}
			}
			housesMap.put(WifiUtils.intToString(i), otherhouses);
		}
		return housesMap;
	}

	public void runTransferAlgorithm() {

		AbstractPredicateWriter apw = new AbstractPredicateWriter();

		init();
		Map<String, List<String>> housesMap = makeHousesMap();

		System.out.println("Nr houses: " + numberHouses);
		System.out.println("housesMap:");
		WifiUtils.printMap(housesMap);

		for (int houseNr = 0; houseNr < houses.length; houseNr++) {
			String house = houses[houseNr];

			// this house becomes target
			System.out.println("\nHouse: " + house); 
			

			// create output directory for house if it doesn't exist yet
			String outputDirName = ROOT_DIR + "output/" + "houseInfo" + house + "/";
			Utils.createDirectory(outputDirName);
			
			// will contain sensor and action data for this house
			List<String> sensorReadings = null;
			List<String> actionReadings = null;
			Map<String, List<String>> actionMap = null;
			Set<String> actionDates = null;
			Map<String, List<String>> sensorMap = null ;				
			ArrayList<String> allDates = null;
			// filenames for raw action and sensor data
			String sensorFile = null;
			String actionFile= null;
			String sensorMapFile= null;
			String actionMapFile= null;
			
			for (int noDaysIndex = 0; noDaysIndex < noDaysArray.length; noDaysIndex++) {
				int noDays = noDaysArray[noDaysIndex];
				System.out.println("\nDay: " + noDays);

				
				// will contain train and test data
				Map<String, List<String>> testActionInstances = new HashMap<String, List<String>>();
				Map<String, List<String>> testSensorInstances = new HashMap<String, List<String>>();
				Map<String, List<String>> trainActionInstances = new HashMap<String, List<String>>();
				Map<String, List<String>> trainSensorInstances = new HashMap<String, List<String>>();				
				
				
				for (FEATURE_TYPE featureType : FEATURE_TYPE.values()) {
					System.out.println("\nFeature type: " + featureType);

					// check if input directory for house and featuretype exists
					String inputDirName = ROOT_DIR + "input/" + featureType + "/" + "houseInfo" + house + "/";
					File inputDir = new File(inputDirName);
					if (!inputDir.exists()) {
						System.out.println("directory with input files not found: " + inputDirName);
						System.exit(1);

					}

					// make output dir for this  noDays
					String outputDirNoDaysName = outputDirName + house + noDays + "/";
					Utils.createDirectory(outputDirNoDaysName);

					// make output dir for this featureType
					String outputDirNoDaysFeatureTypeName = outputDirNoDaysName + featureType + "/";
					Utils.createDirectory(outputDirNoDaysFeatureTypeName);

					
					// ======== read in lines with dates and activities  ======== 					 
					// NOTE: if-check is used to make sure we only read in once per house
					// 		and not read the files and make the maps again for each nr. of days, or for each featuretype
					if (sensorReadings == null) {
						
						// file with lines consisting of a date range and a sensor id (that fire during this interval)
						sensorFile = new File(inputDirName, "house" + house + "-ss.txt").getAbsolutePath();

						// file with lines consisting of a date range and an action id (that happens during this interval)
						actionFile = new File(inputDirName, "house" + house + "-as.txt").getAbsolutePath();

						// file with corresponding sensor ids, descriptions, and meta-features
						sensorMapFile = new File(inputDirName, "sensorMap" + house + "-ids.txt").getAbsolutePath();

						// file with mapping action ids to their descriptions
						actionMapFile = new File(inputDirName, "actionMap" + house + ".txt").getAbsolutePath();
						
						sensorReadings = WifiUtils.getLines(sensorFile);
						actionReadings = WifiUtils.getLines(actionFile);
	
						// WifiUtils.printList(sensorReadings);
						// WifiUtils.printList(actionReadings);
	
						System.out.println("sensorReadings size: " + sensorReadings.size());
						System.out.println("actionReadings size: " + actionReadings.size());
	
						// construct a map of date (day) to activities
						actionMap = new HashMap<String, List<String>>();
						saveActionLinesByDate(actionReadings, actionMap);
						// construct a map of date to sensor readings
						actionDates = actionMap.keySet();
						sensorMap = saveSensorLinesByDate(sensorReadings, actionDates);
						
						allDates = new ArrayList<String>(actionMap.keySet());
						
						maxDaysPlotPerHouse[houseNr] = actionMap.size()-1; 
						
						System.out.println("max days for house " + house +": " + maxDaysPlotPerHouse[houseNr]);
						// System.out.println("actionMap:");
						// WifiUtils.printMap(actionMap);
						//System.out.println("actionMap (per day) size: " + actionMap.size());
						// System.out.println("sensorMap:");
						// WifiUtils.printMap(sensorMap);
						//System.out.println("sensorMap (per day) size: " + actionMap.size());
					}
					
					if (maxDaysPlotPerHouse[houseNr] >= noDays) {
					
						// ======== construct test and training data  ======== 
						// NOTE: we only make training and testing lines  when not made yet for the
						// 		first featuretype
						// 		because we want both featuretypes to use the same train and test data		
						if (testActionInstances.isEmpty() && testSensorInstances.isEmpty()){
									
	
							testActionInstances = new HashMap<String, List<String>>();
							testSensorInstances = new HashMap<String, List<String>>();
	
							trainActionInstances = new HashMap<String, List<String>>();
							trainSensorInstances = new HashMap<String, List<String>>();
	
							getTestAndTrainingSetsLeaveOneOut(noDays, actionMap, sensorMap, allDates, testActionInstances, testSensorInstances, trainActionInstances, trainSensorInstances);
						}
						
						// WifiUtils.printMapNewlines(testActionInstances);
						//System.out.println("nr. train/test sets in testActionInstances: ");
						//System.out.println(testActionInstances.keySet().size());
	
						// WifiUtils.printMapNewlines(trainActionInstances);
						//System.out.println("nr. train/test sets in trainActionInstances: ");
						//System.out.println(trainActionInstances.keySet().size());
	
						// for each train/test set
						// dirName = id , denotes a specific train+test set combi
						Set<String> sn = testActionInstances.keySet();
						String[] setNames = sn.toArray(new String[0]);
						int setSize = setNames.length;
	
						System.out.println("Processing train+test set id: ");
						for (int setIndex=0; setIndex < setSize; setIndex++) {
							System.out.print( "\t" +(setIndex+1) + "/" + setSize );
							if ((setIndex+1) % 8 == 0) {
								System.out.println();
							}
							String dirName = setNames[setIndex];
							File outputDirNoDaysSplit = new File(outputDirNoDaysFeatureTypeName, "split" + dirName);
							Utils.deleteDir(outputDirNoDaysSplit.getAbsolutePath());
							outputDirNoDaysSplit.mkdir();
	
							// save test data
							String sensorTestFile = new File(outputDirNoDaysSplit, "house" + house + "-ss-test.txt").getAbsolutePath();
							String actionTestFile = new File(outputDirNoDaysSplit, "house" + house + "-as-test.txt").getAbsolutePath();
							List<String> test_actions = testActionInstances.get(dirName);
							List<String> test_sensors = testSensorInstances.get(dirName);
							Utils.saveLines(test_actions, actionTestFile);
							Utils.saveLines(test_sensors, sensorTestFile);
	
							// save training data
							String sensorTrainFile = new File(outputDirNoDaysSplit, "house" + house + "-ss-train.txt").getAbsolutePath();
							String actionTrainFile = new File(outputDirNoDaysSplit, "house" + house + "-as-train.txt").getAbsolutePath();
							List<String> actions = trainActionInstances.get(dirName);
							List<String> sensors = trainSensorInstances.get(dirName);
							Utils.saveLines(sensors, sensorTrainFile);
							Utils.saveLines(actions, actionTrainFile);
	
							// ======== build no transfer model ========
							String outputDirNoDaysFeatureTypeNameTransferTypeName = outputDirNoDaysFeatureTypeName + TRANSFER_TYPE.NOTRANSFER + "/";
							Utils.createDirectory(outputDirNoDaysFeatureTypeNameTransferTypeName);
	
							getFeatureRepresentationOfTrainAndTestDataForNoTransferCase(apw, sensorMapFile, actionMapFile, outputDirNoDaysFeatureTypeNameTransferTypeName, dirName, outputDirNoDaysSplit,
									sensorTrainFile, actionTrainFile, sensorTestFile, actionTestFile, conf, noDaysIndex, houseNr, featureType);
	
							// ======== get rules from all domains for transfer ========
							String outputFileCombined = new File(outputDirNoDaysSplit, Constants.WIFI_EXAMPLES_FILE + "-train-tr").getAbsolutePath();
							String outputAbstructFileCombined = new File(outputDirNoDaysSplit, Constants.WIFI_ABSTRUCT_EXAMPLES_FILE + "-train-tr").getAbsolutePath();
							String outputMapFileCombined = new File(outputDirNoDaysSplit, Constants.WIFI_CLASS_MAP_FILE + "-train-tr").getAbsolutePath();
	
							// get a copy of original sensor model
							Map<String, List<EventInfo>> consecutiveIntervalsTarget = new TreeMap<String, List<EventInfo>>();
							Map<String, Sensor> sensorModelsComb = new TreeMap<String, Sensor>();
							WifiAligner.getAlignedSensorData(sensorTrainFile, actionTrainFile, sensorMapFile, actionMapFile, consecutiveIntervalsTarget, sensorModelsComb);
	
							// combine training data from different houses
							Map<String, List<EventInfo>> consecutiveIntervals = new HashMap<String, List<EventInfo>>();
							combineTrainingData(housesMap, house, sensorModelsComb, consecutiveIntervals, featureType);
							// saveSensorModel(sensorModelsComb, "sensorModelComb");
	
							// save basic relations
							WifiAligner.getPredicates(consecutiveIntervals, sensorModelsComb, outputFileCombined, false);
							// save abstract relations
							WifiAligner.saveAbstructRelations(outputFileCombined, outputAbstructFileCombined, outputMapFileCombined, withRanges, null);
							// save rules
							String rulesFileTransfer = new File(outputDirNoDaysSplit, "rules.txt").getAbsolutePath();
							getRules(outputMapFileCombined, rulesFileTransfer, confTr);
	
							// ======== represent data in a new domain in terms of these rules ============
	
							// get target training data represented in terms of the
							// extended model
							String outputTargetFile = new File(outputDirNoDaysSplit, Constants.WIFI_EXAMPLES_FILE + "-train-trmodel-target").getAbsolutePath();
							WifiAligner.getPredicates(consecutiveIntervalsTarget, sensorModelsComb, outputTargetFile, true);
	
							String outputAbstructFileTarget = new File(outputDirNoDaysSplit, Constants.WIFI_ABSTRUCT_EXAMPLES_FILE + "-train-trmodel-target").getAbsolutePath();
							String outputMapFileTarget = new File(outputDirNoDaysSplit, Constants.WIFI_CLASS_MAP_FILE + "-train-trmodel-target").getAbsolutePath();
							WifiAligner.saveAbstructRelations(outputTargetFile, outputAbstructFileTarget, outputMapFileTarget, withRanges, null);
	
							// extract new rules from the domain data
							String rulesFileTargetExtModel = new File(outputDirNoDaysSplit, "rules-target-extmodel.txt").getAbsolutePath();
							getRules(outputMapFileTarget, rulesFileTargetExtModel, conf);
	
							// combine the rules extracted from source and target
							// domains
							String targetRulesFileComb = new File(outputDirNoDaysSplit, "rules-comb.txt").getAbsolutePath();
							List<String> lines1 = Utils.readLines(rulesFileTargetExtModel);
							List<String> lines2 = Utils.readLines(rulesFileTransfer);
							lines2.addAll(lines1);
							Utils.saveLines(lines2, targetRulesFileComb);
	
							outputDirNoDaysFeatureTypeNameTransferTypeName = outputDirNoDaysFeatureTypeName + TRANSFER_TYPE.TRANSFER + "/";
							Utils.createDirectory(outputDirNoDaysFeatureTypeNameTransferTypeName);
							// represent domain training data in terms of new
							// features
	
							String resultFile2 = new File(outputDirNoDaysFeatureTypeNameTransferTypeName, FILE_TYPE.TRAIN + "/" + "wifi" + dirName).getAbsolutePath();
							apw.getFeatureRepresentationOfData(outputTargetFile, resultFile2, targetRulesFileComb, classMapFile, USE_CLASS);
	
							// represent domain test data in terms of new features					
							String resultFileTest2 = new File(outputDirNoDaysFeatureTypeNameTransferTypeName, FILE_TYPE.TEST + "/" + "wifi" + dirName).getAbsolutePath();
							getFeatureRepresentationOfTestData(outputDirNoDaysSplit, sensorTestFile, actionTestFile, sensorMapFile, actionMapFile, sensorModelsComb, resultFileTest2, targetRulesFileComb,
									"transfer", apw);
						}					
					}
				}				
			}
		}
	}

	public void evaluateUsingSVM() {

		results = new double[numberHouses][noDaysArray.length][FEATURE_TYPE.length()][TRANSFER_TYPE.length()];

		System.out.println("\nGoing to evaluate using libSVM...");

		for (int houseNr = 0; houseNr < numberHouses; houseNr++) {
			String outputDirHouse = ROOT_DIR + "output/" + "houseInfo" + houses[houseNr] + "/";

			for (int noDaysIndex = 0; noDaysIndex < noDaysArray.length; noDaysIndex++) {
				int noDays = noDaysArray[noDaysIndex];
				
				if (maxDaysPlotPerHouse[houseNr] > noDays){
					String outputDirHouseDays = outputDirHouse + houses[houseNr] + noDays + "/";
	
					for (FEATURE_TYPE fT : FEATURE_TYPE.values()) {
	
						for (TRANSFER_TYPE tT : TRANSFER_TYPE.values()) {
	
							String testDir = outputDirHouseDays + fT + "/" + tT + "/" + FILE_TYPE.TEST + "/";
							String trainDir = outputDirHouseDays + fT + "/" + tT + "/" + FILE_TYPE.TRAIN + "/";
	
							ArrayList<String> testFiles = Utils.getDirectoryListing(testDir);
							ArrayList<String> trainFiles = Utils.getDirectoryListing(trainDir);
	
							String tempOutputDir = outputDirHouseDays + fT + "/" + tT + "/" + "tempOutput/";
							double total = 0;
							for (int fileNameIndex = 0; fileNameIndex < trainFiles.size(); fileNameIndex++) {
								total += callSVM(trainDir, testDir, tempOutputDir, trainFiles.get(fileNameIndex), testFiles.get(fileNameIndex));
							}
							total /= trainFiles.size();
	
							results[houseNr][noDaysIndex][fT.index()][tT.index()] = total;
	
							Utils.deleteDir(tempOutputDir);
						}
					}
				}
			}
		}
		System.out.println("Done evaluating using libSVM.");
		//printEvaluationResults() ;
	}

	public void printEvaluationResults() {
		for (int houseNr = 0; houseNr < numberHouses; houseNr++) {
			System.out.println("House: " + houses[houseNr]);

			for (int noDaysIndex = 0; noDaysIndex < noDaysArray.length; noDaysIndex++) {
				int noDays = noDaysArray[noDaysIndex];
				System.out.println("NoDays: " + noDays);

				for (FEATURE_TYPE fT : FEATURE_TYPE.values()) {
					System.out.println("FT: " + fT);

					for (TRANSFER_TYPE tT : TRANSFER_TYPE.values()) {
						System.out.println("TT: " + tT);
						System.out.println("Result:" + results[houseNr][noDaysIndex][fT.index()][tT.index()]);
					}
				}
			}
		}
	}

	public double callSVM(String trainDir, String testDir, String tempOutputDir, String trainFile, String testFile) {
		// System.out.println("trainFile: " + trainFile);
		Utils.createDirectory(tempOutputDir);

		// train
		ArrayList<String> svmTrainerArgs = new ArrayList<String>();
		svmTrainerArgs.add("-q"); // quiet mode
		svmTrainerArgs.add(trainDir + trainFile);
		svmTrainerArgs.add(tempOutputDir + trainFile + "_outputtrainfile.txt");
		svm_train svmTrainer = new svm_train();
		svmTrainer.run(svmTrainerArgs.toArray(new String[0]));

		// test
		ArrayList<String> svmPredictorArgs = new ArrayList<String>();
		svmPredictorArgs.add(testDir + testFile);
		svmPredictorArgs.add(tempOutputDir + trainFile + "_outputtrainfile.txt");
		svmPredictorArgs.add(tempOutputDir + testFile + "_outputtestfile.txt");
		svm_predict svmPredictor = new svm_predict();
		double accuracy = svmPredictor.run(svmPredictorArgs.toArray(new String[0]));

		return accuracy / 100.0;
	}

	public void evaluationResultsToMatlabPerHouse() {
		String matlabDir = ROOT_DIR + "output/" + "matlab/";
		String plotOutputDir = new File(matlabDir).getAbsolutePath();
		Utils.createDirectory(matlabDir);

		for (int houseNr = 0; houseNr < numberHouses; houseNr++) {

			BufferedWriter bw = null;
			try {
				File file = new File(matlabDir + "dataHouse" + houses[houseNr] + ".m");
				bw = new BufferedWriter(new FileWriter(file));

				String dvString = "datavalues";

				for (FEATURE_TYPE fT : FEATURE_TYPE.values()) {
					String ftString = fT == FEATURE_TYPE.OF ? dvString + "_of" : dvString + "_hf";

					for (TRANSFER_TYPE tT : TRANSFER_TYPE.values()) {
						String ttString = tT == TRANSFER_TYPE.NOTRANSFER ? ftString + "_notr" : ftString + "_tr";

						bw.write(ttString + "= [");
						for (int noDaysIndex = 0; noDaysIndex < noDaysArray.length; noDaysIndex++) {
							if (noDaysArray[noDaysIndex] < maxDaysPlotPerHouse[houseNr]) {
								// int noDays = noDaysArray[noDaysIndex];
								bw.write(Double.toString(results[houseNr][noDaysIndex][fT.index()][tT.index()]));
	
								if (noDaysIndex < noDaysArray.length - 1) {
									bw.write(", ");
								}
							}
						}
						bw.write("];\n");
					}
				}
				bw.write("datapoints = [");
				for (int nD = 0; nD < noDaysArray.length && noDaysArray[nD] < maxDaysPlotPerHouse[houseNr]; nD++) {
					if (noDaysArray[nD] < maxDaysPlotPerHouse[houseNr]) {
						bw.write(Integer.toString(noDaysArray[nD] - 1));
						bw.write(" ");
					}
				}
				bw.write("];\n");

				bw.write("directory='" + plotOutputDir + "/';\n");
				bw.write("housename=' " + houses[houseNr] + "';\n");
				bw.write("addpath ../../input/matlab/\n");
				bw.write("run ../../input/matlab/saveplot;");

			}
			catch (IOException e) {
				e.printStackTrace();
			}
			finally {
				if (bw != null) {
					try {
						bw.close();
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		System.out.println("\nWriting to Matlab files done.");
		System.out.println("See directory " + matlabDir + " for the matlab scripts.");
	}

	/**
	 * Constructs a mapping of date/time to sensor readings
	 * 
	 * @param actionReadings - date+sensor readings string
	 * @param actionMap - maps date to sensor readings
	 */
	private Map<String, List<String>> saveSensorLinesByDate(List<String> sensorReadings, Set<String> actionDates) {
		Map<String, List<String>> sensorMap = new HashMap<String, List<String>>();
		for (String sensorReading : sensorReadings) {
			String[] sensorInfo = sensorReading.split("\\s+");
			String date = sensorInfo[0];
			if (!actionDates.contains(date)) { // only keep annotated readings
				continue;
			}
			List<String> lines = sensorMap.get(date);
			if (lines == null) {
				lines = new ArrayList<String>();
			}
			lines.add(sensorReading);
			sensorMap.put(date, lines);
		}
		return sensorMap;
	}

	
	/**
	 * Constructs a mapping of date/time to activities
	 * 
	 * @param actionReadings- date+action string
	 * @param actionMap - maps date to activities info
	 */
	private void saveActionLinesByDate(List<String> actionReadings, Map<String, List<String>> actionMap) {
		String prevDate = null;
		for (String actionReading : actionReadings) {
			String[] sensorInfo = actionReading.split("\\s+"); // split on whitespace

			String date = sensorInfo[0];

			if (prevDate != date && prevDate != null) { // only tests for null			
				List<String> lines = actionMap.get(prevDate);
				if (lines == null) {
					lines = new ArrayList<String>();
				}
				lines.add(actionReading);
				actionMap.put(prevDate, lines);
			}
			else {
				List<String> lines = actionMap.get(date);
				if (lines == null) {
					lines = new ArrayList<String>();
				}
				lines.add(actionReading);
				actionMap.put(date, lines);
			}
			prevDate = date;
		}
	}

	/**
	 * First models sensors to convert the original data to abstract logic form,
	 * then extracts rules from this data representation. Finally, represents
	 * both training and test data in terms of these rules. The new, vector
	 * representation will be then used to train a classifier, e.g. SVN.
	 * 
	 * @param apw - AbstractPredicateWriter converts the data to abstract logic
	 *            form
	 * @param sensorMapFile - file with corresponding sensor ids, descriptions,
	 *            and meta-features a map of sensor readings
	 * @param actionMapFile - file with lines consisting of a date range and an
	 *            action idhat happens during this interval)
	 * @param rootDirHouse- a sub-directory with data associated with a
	 *            particular house
	 * @param dirName - date identifier
	 * @param rootDir_ - root directory
	 * @param sensorTrainFile- training file with sensor firings information
	 * @param actionTrainFile- training file with activity information
	 * @param sensorTestFile - test file with sensor firings information
	 * @param actionTestFile- test file with activity information
	 * @param conf - minimum confidence threshold
	 */
	private void getFeatureRepresentationOfTrainAndTestDataForNoTransferCase(AbstractPredicateWriter apw, String sensorMapFile, String actionMapFile, String rootDirHouse, String dirName,
			File rootDir_, String sensorTrainFile, String actionTrainFile, String sensorTestFile, String actionTestFile, String conf, int noDaysIndex, int houseNr, FEATURE_TYPE featureType) {

		Map<String, List<EventInfo>> consecutiveIntervals = new TreeMap<String, List<EventInfo>>();
		Map<String, Sensor> sensorModels = new TreeMap<String, Sensor>();

		// fills in consecutiveIntervals and sensorModels maps
		WifiAligner.getAlignedSensorData(sensorTrainFile, actionTrainFile, sensorMapFile, actionMapFile, consecutiveIntervals, sensorModels);

		//System.out.println("consecutiveIntervals: ");
		//WifiUtils.printEventInfoMap(consecutiveIntervals);
		/*
		 * e.g. 8-2-2008-use-toilet-154 --> type = BathroomDoor actionType =
		 * use-toilet sensorDurations = 1 1 sensorStarts = 673 675 sensorEnds =
		 * 672 675 actionDuration = 3 actionStart = 672 actionEnd = 675 type =
		 * Toilet actionType = use-toilet sensorDurations = 1 sensorStarts = 676
		 * sensorEnds = 675 actionDuration = 3 actionStart = 672 actionEnd = 675
		 * type = BathroomDoor actionType = use-toilet sensorDurations = 1 1
		 * sensorStarts = 673 675 sensorEnds = 672 675 actionDuration = 3
		 * actionStart = 672 actionEnd = 675
		 */
		//System.out.println("sensorModels: ");
		//WifiUtils.printSensorTreeMap(sensorModels);
		/*
		 * e.g. KitchHeat --> |||action name leave-house sensor durations: [1]
		 * s.d. median: 1 no firings: [1] no firings median: 1 action durations:
		 * [385] a.d. median: 385 , sensor starts: [1035] s.d. starts median:
		 * 1035 sensor ends: [1034] s.d. ends median: 1035 action starts: [649]
		 * s.d. starts median: 649 action ends: [1034] s.d. ends median: 1034|||
		 * |||action name prep-dinner sensor durations: [1, 1, 1, 10071] s.d.
		 * median: 1 no firings: [1, 3] no firings median: 2 action durations:
		 * [25, 45] a.d. median: 35 , sensor starts: [1096, 1107, 1120, 1136]
		 * s.d. starts median: 1114 sensor ends: [1096, 1107, 1119, 1145] s.d.
		 * ends median: 1114 action starts: [1095, 1134] s.d. starts median:
		 * 1115 action ends: [1120, 1179] s.d. ends median: 1150||| |||action
		 * name prep-breakfast sensor durations: [1, 1, 1, 1] s.d. median: 1 no
		 * firings: [1, 1, 1, 1] no firings median: 1 action durations: [3, 3,
		 * 6, 6] a.d. median: 5 , sensor starts: [580, 554, 549, 503] s.d.
		 * starts median: 552 sensor ends: [579, 553, 549, 502] s.d. ends
		 * median: 552 action starts: [578, 552, 544, 501] s.d. starts median:
		 * 548 action ends: [584, 555, 550, 504] s.d. ends median: 553|||
		 */

		// using consecutiveIntervals and sensorModels maps give data logic
		// representation

		String outputTargetFile = new File(rootDir_, Constants.WIFI_EXAMPLES_FILE + "-train-notr").getAbsolutePath();
		WifiAligner.getPredicates(consecutiveIntervals, sensorModels, outputTargetFile, true);

		// given a simple logic data representation obtained above, create
		// abstract data representation
		// by making generalizations over predicates
		// make two files:
		// WIFI_ABSTRUCT_EXAMPLES_FILE + "-train-notr"
		// WIFI_CLASS_MAP_FILE + "-train-notr"
		// NOTE: this doesn't seem to add much..
		// NOTE: full path+filename is appended at the end of the files
		String outputAbstructTargetFile = new File(rootDir_, Constants.WIFI_ABSTRUCT_EXAMPLES_FILE + "-train-notr").getAbsolutePath();
		String outputMapTargetFile = new File(rootDir_, Constants.WIFI_CLASS_MAP_FILE + "-train-notr").getAbsolutePath();
		WifiAligner.saveAbstructRelations(outputTargetFile, outputAbstructTargetFile, outputMapTargetFile, withRanges, null);

		// extract rules/features from the abstract data
		// NOTE: uses RFE algorithm ( with FPgrowth)
		// uses train data
		String targetRulesFile = new File(rootDir_, "rules-target.txt").getAbsolutePath();
		getRules(outputMapTargetFile, targetRulesFile, conf);

		// WifiUtils.stop();

		// represent training data in terms of extracted rules
		// NOTE: for SVM
		String svmFileTrain = new File(rootDirHouse, FILE_TYPE.TRAIN + "/" + "wifi" + dirName).getAbsolutePath();
		apw.getFeatureRepresentationOfData(outputTargetFile, svmFileTrain, targetRulesFile, classMapFile, USE_CLASS);

		// also do for test data (but use extracted rules of train data?)

		String svmFileTest = new File(rootDirHouse, FILE_TYPE.TEST + "/" + "wifi" + dirName).getAbsolutePath();
		getFeatureRepresentationOfTestData(rootDir_, sensorTestFile, actionTestFile, sensorMapFile, actionMapFile, sensorModels, svmFileTest, targetRulesFile, "notransfer", apw);
	}

	/**
	 * Constructs a training and a test set, such that noDays are selected for
	 * training and one instance out of this set is used for testing in turn.
	 * Thus, noDays test and training sets are created in total, where each
	 * training set contains noDays-1 instances, and a test set contains a
	 * single instance, different for each of the sets.
	 * 
	 * 
	 * @param noDays - number of days/instances to be used for training/testing
	 * @param actionMap - date to activity map
	 * @param sensorMap - date to sensor reading map
	 * @param allDates a list of all dates present in the data set
	 * @param testActionInstances - test set maps with action instances
	 * @param testSensorInstances test set maps with sensor instances
	 * @param trainActionInstances - training set maps with action instances
	 * @param trainSensorInstances - training set maps with sensor instances
	 */
	private void getTestAndTrainingSetsLeaveOneOut(int noDays, Map<String, List<String>> actionMap, Map<String, List<String>> sensorMap, List<String> allDates,
			Map<String, List<String>> testActionInstances, Map<String, List<String>> testSensorInstances, Map<String, List<String>> trainActionInstances, Map<String, List<String>> trainSensorInstances) {

		for (int k = 0; k < NO_DATA_INSTANCES; k++) {
			List<String> instanceDates = new ArrayList<String>();

			int cnt = 0;
			int stop = 0;
			while (cnt < noDays && stop < 100) {
				int index = rand.nextInt(allDates.size() - 1);
				String date = allDates.get(index);
				List<String> actList = actionMap.get(date);

				if (actList.size() > 4 && !instanceDates.contains(date)) {
					instanceDates.add(date);
					cnt++;
				}
				stop++;
			}
			// System.out.println("instanceDates:");
			// WifiUtils.printList(instanceDates);

			for (int i = 0; i < instanceDates.size(); i++) {
				List<String> idSet = new ArrayList<String>();

				String testDate = instanceDates.get(i);
				// System.out.println("testDate: " + testDate + ", index: " +
				// i);

				String[] dateInfotest = testDate.split("-");
				// String id = dateInfotest[0] + "-";

				// NOTE: if below line is commented,
				// noDays train/test combi's will be added, one
				// for each day in instanceDates (the test day)
				// idSet.add(dateInfotest[0]);

				// System.out.println("idSet:");
				// WifiUtils.printList(idSet);

				// create test and training sets
				List<String> actionTests = new ArrayList<String>();
				List<String> sensorTests = new ArrayList<String>();
				actionTests.addAll(actionMap.get(testDate));
				sensorTests.addAll(sensorMap.get(testDate));

				List<String> actionTrainSet = new ArrayList<String>();
				List<String> sensorTrainSet = new ArrayList<String>();
				List<String> trainDates = new ArrayList<String>(instanceDates);
				trainDates.remove(i);

				// System.out.println("trainDates:");
				// WifiUtils.printList(trainDates);

				for (String trainDate : trainDates) {
					String[] dateInfo = trainDate.split("-");
					// id += dateInfo[0] + "-";
					idSet.add(dateInfo[0] + dateInfo[1]);

					actionTrainSet.addAll(actionMap.get(trainDate));
					sensorTrainSet.addAll(sensorMap.get(trainDate));
				}
				Collections.sort(idSet);
				String id = "";
				for (String id_ : idSet) {
					id += id_ + "-";
				}
				id = id.substring(0, id.length() - 1);

				// System.out.println("id: " + id);

				if (trainActionInstances.containsKey(id)) {
					continue;
				}

				trainActionInstances.put(id, actionTrainSet);
				trainSensorInstances.put(id, sensorTrainSet);
				testActionInstances.put(id, actionTests);
				testSensorInstances.put(id, sensorTests);

			}
		}

	}

	/**
	 * @param rootDir - root directory
	 * @param sensorTestFile - test file with sensor firings information
	 * @param actionTestFile - test file with activities information
	 * @param sensorMapFile - file with mapping action ids to their descriptions
	 * @param actionMapFile - file with lines consisting of a date range and an
	 *            action id (that happens during this interval)
	 * @param sensorModels - sensor models built form training data
	 * @param svmFileTest - file to which data represented in SVM format - in
	 *            terms of rules/features is stored
	 * @param rulesFile- file with rules/features
	 * @param transferType - string that will be used in all output file names,
	 *            e.g. "transfer" or "notransfer".
	 * @param apw - AbstractPredicateWriter to convert test data to a new
	 *            feature form.
	 */
	private void getFeatureRepresentationOfTestData(File rootDir_, String sensorTestFile, String actionTestFile, String sensorMapFile, String actionMapFile, Map<String, Sensor> sensorModels,
			String svmFileTest, String rulesFile, String transferType, AbstractPredicateWriter apw) {

		String outputFileTest = new File(rootDir_, Constants.WIFI_EXAMPLES_FILE + "-test-" + transferType).getAbsolutePath();
		String outputAbstructFileTest = new File(rootDir_, Constants.WIFI_ABSTRUCT_EXAMPLES_FILE + "-test-" + transferType).getAbsolutePath();
		String outputMapFileTest = new File(rootDir_, Constants.WIFI_CLASS_MAP_FILE + "-test-" + transferType).getAbsolutePath();

		Map<String, List<EventInfo>> consecutiveIntervalsTest = new TreeMap<String, List<EventInfo>>();
		Map<String, Sensor> sensorModelsTest = new HashMap<String, Sensor>();
		// use non-transfer model
		// // NOTE:/ TODO: this is where models don't overlap
		WifiAligner.getAlignedSensorData(sensorTestFile, actionTestFile, sensorMapFile, actionMapFile, consecutiveIntervalsTest, sensorModelsTest);

		WifiAligner.getPredicates(consecutiveIntervalsTest, sensorModels, outputFileTest, true);

		WifiAligner.saveAbstructRelations(outputFileTest, outputAbstructFileTest, outputMapFileTest, withRanges, null);
		apw.getFeatureRepresentationOfData(outputFileTest, svmFileTest, rulesFile, classMapFile, USE_CLASS);
	}

	/**
	 * Runs an association algorithm FPGrowthDriver to extract frequent rules
	 * informative of class.
	 * 
	 * @param outputMapFile - a file with the following information:
	 *            class1:num_values_in_class1, class2:num_vlaues_in_class2, ...
	 *            + a full path to abstract relational data representation file
	 * 
	 * @param rulesFile - a file where rules will be stored to
	 * @param conf - minimum confidence
	 */
	private void getRules(String outputMapFile, String rulesFile, String conf) {
		try {
			String[] params = { "--input", outputMapFile, "--output", rulesFile, "--method", "sequential", "--encoding", "US-ASCII", "--splitterPattern", ":", "--minSupport", conf };

			FPGrowthDriver fpGrowthDriver = new FPGrowthDriver();
			fpGrowthDriver.runFPGrowthDriver(params);

		}
		catch (Exception e) {
			System.out.println("[ERROR]: Unexpected exception.");
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Fills sensorModelsTarget
	 * 
	 * @param housesMap - maps each house id, e.g. A,B,C to the remaining
	 *            houses, e.g. A-> B,C
	 * @param house - current house id
	 * @param sensorModelsTarget - a sensor model obtained from data coming from
	 *            all houses
	 * @param consecutiveIntervals - aligned sensor and activity information
	 */
	private void combineTrainingData(Map<String, List<String>> housesMap, String house, Map<String, Sensor> sensorModelsTarget, Map<String, List<EventInfo>> consecutiveIntervals, FEATURE_TYPE ft) {
		List<String> sourceDomains = housesMap.get(house);

		List<Map<String, Sensor>> sensorModelsAll = new ArrayList<Map<String, Sensor>>();

		for (String sourceHouse : sourceDomains) {
			String inputDirThisHouse = ROOT_DIR + "input/" + ft + "/" + "houseInfo" + sourceHouse + "/";
			String sensorFile_ = new File(inputDirThisHouse, "house" + sourceHouse + "-ss.txt").getAbsolutePath();
			String actionFile_ = new File(inputDirThisHouse, "house" + sourceHouse + "-as.txt").getAbsolutePath();
			String sensorMapFile_ = new File(inputDirThisHouse, "sensorMap" + sourceHouse + "-ids.txt").getAbsolutePath();
			String actionMapFile_ = new File(inputDirThisHouse, "actionMap" + sourceHouse + ".txt").getAbsolutePath();

			Map<String, Sensor> sensorModels = new HashMap<String, Sensor>();
			WifiAligner.getAlignedSensorData(sensorFile_, actionFile_, sensorMapFile_, actionMapFile_, consecutiveIntervals, sensorModels);
			sensorModelsAll.add(sensorModels);
		}
		// NOTE: here sensor models are merged
		mergeSensorModels(sensorModelsTarget, sensorModelsAll);
	}

	private void mergeSensorModels(Map<String, Sensor> sensorModelsTarget, List<Map<String, Sensor>> sensorModelsAll) {

		for (Map<String, Sensor> sensorModels : sensorModelsAll) {
			for (String sensor : sensorModels.keySet()) {
				Sensor ss = sensorModels.get(sensor);
				if (ss == null) { // can not happen
					continue;
				}
				Sensor ts = sensorModelsTarget.get(sensor);
				if (ts == null) {
					sensorModelsTarget.put(sensor, ss);
				}
				else {
					ts.merge(ss);
					sensorModelsTarget.put(sensor, ts);
				}
			}
		}

		//		for (String sensor : sensorModelsTarget.keySet()) {
		//			Sensor s = sensorModelsTarget.get(sensor);
		//			// s.printCountInfo();
		//			//System.out.println(sensor);
		//			s.printActionInfo(true);
		//
		//		}
	}
	
	

}
