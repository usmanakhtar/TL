package glue;

import java.io.File;
import java.util.ArrayList;

import data.HouseData;

import art.framework.utils.*;
import art.experiments.*;
import art.experiments.wifi.data.processor.WifiUtils;

/**
 * Runs the whole stuff: metafeature making + transfer algorithm resp. from
 * our_project_project and from arf.experiments.wifi
 * 
 * 
 */
public class ExperimentRunner {

	private WifiExperimentRunner wer;
	
	private String rootOutputDir = "../our_project_project/output/";


	public ExperimentRunner() {
		// constructor

	}

	
	public void experiment1MakeFiles(int subsetMin, int subsetMax) {

		Utils.resetDirectory(rootOutputDir);
		Utils.resetDirectory(WifiExperimentRunnerOld.EXP_DIR);
		
		
		for (int i=subsetMin; i < subsetMax; i++) {
			Utils.createDirectory(WifiExperimentRunnerOld.EXP_DIR + HouseData.houseOutputDirPrefix + MetaFeatureMaker.allHouseNames[i]);
			System.out.println("Created dir " + WifiExperimentRunnerOld.EXP_DIR + HouseData.houseOutputDirPrefix + MetaFeatureMaker.allHouseNames[i]);
		}
		
		MetaFeatureMaker.runForSubset(rootOutputDir, subsetMin, subsetMax, 
				WERenums.MF_TYPE.AUTO, WERenums.CLUSTER_TYPE.CT_ABS, WERenums.PROFILE_TYPE.PR_SP, WERenums.TRANSFER_SETTINGS.ONLY_TRANSFER);

		MetaFeatureMaker.runForSubset(rootOutputDir, subsetMin, subsetMax, 
				WERenums.MF_TYPE.AUTO, WERenums.CLUSTER_TYPE.CT_REL, WERenums.PROFILE_TYPE.PR_SP, WERenums.TRANSFER_SETTINGS.ONLY_TRANSFER);

		// copy our created experiment files to input dir
		copyOutputToWifiExperimentRunnerInput(subsetMin, subsetMax, rootOutputDir, WifiExperimentRunnerOld.EXP_DIR);

		// copy (part of) her original features to input dir
		copyOriginalHCToWifiExperimentRunnerInput(subsetMin, subsetMax, WERenums.TRANSFER_SETTINGS.BOTH);
		
	}
	
	public void experiment1() {



		int subsetMin = 3;
		int subsetMax = 5;

		
		//experiment1MakeFiles(subsetMin, subsetMax);
		
		wer = new WifiExperimentRunner();

		wer.setSubset(subsetMin, subsetMax);
		wer.set_NO_DATA_INSTANCES(5);
		int[] noDaysConsidered = { 2, 3 };
		wer.setNoDaysArray(noDaysConsidered);
		wer.turnLoggingOff();
		wer.setWithRanges(true);

		System.out.println("\n------ WER settings : ------");
		System.out.println(wer);
		System.out.println("----------------------------\n");

		wer.run();

	}

	public void copyOriginalHCToWifiExperimentRunnerInput(int subsetMin, int subsetMax, WERenums.TRANSFER_SETTINGS transferSettings){
		
		ArrayList<String> allHousesDir = Utils.getSubDirectories(WifiExperimentRunnerOld.HC_MMF_DIR);
		for (int i=subsetMin; i < subsetMax; i++) {
			System.out.println(allHousesDir.get(i));
			
			//Utils.createDirectory(WifiExperimentRunner.EXP_DIR + allHousesDir.get(i) + "/" + 
				//			WERenums.FEATURE_TYPE.HF + " " + WERenums.TRANSFER_SETTINGS.BOTH + "/");
			
			//WifiUtils.stop();
//			System.out.println("Copying " +
//					WifiExperimentRunner.HC_MMF_DIR + allHousesDir.get(i) + " \nto\n  " +
//					WifiExperimentRunner.EXP_DIR + allHousesDir.get(i) + "/" + 
//					WERenums.FEATURE_TYPE.HF + " " + WERenums.TRANSFER_SETTINGS.BOTH + "/" );
			Utils.copyDirectory(new File(WifiExperimentRunnerOld.HC_MMF_DIR + allHousesDir.get(i) + "/"),
					new File(WifiExperimentRunnerOld.EXP_DIR + allHousesDir.get(i) + "/" + 
							WERenums.MMF_TYPE.HF + " " + WERenums.TRANSFER_SETTINGS.BOTH + "/"));

		}
		
	}

	public void copyOutputToWifiExperimentRunnerInput(int subsetMin, int subsetMax, String from, String to) {

		String outputDirName = WifiExperimentRunnerOld.EXP_DIR;
		//Utils.resetDirectory(outputDirName);
		String inputDirName = MetaFeatureMaker.outputDirName;
		File inputDir = new File(inputDirName);

		ArrayList<String> outputtedHousesDir = Utils.getSubDirectories(inputDirName);
		//System.out.println("Listing of " + inputDir.getAbsolutePath());
		for (int i=0; i < subsetMax-subsetMin; i++) {
			String houseDir = outputtedHousesDir.get(i);
			//System.out.println("copying " + inputDirName + "/" + houseDir + " to " + outputDirName + houseDir);
			Utils.copyDirectory(new File(inputDirName + "/" + houseDir), new File(outputDirName + houseDir));
		}
	}

	public static void main(String[] args) {
		ExperimentRunner t = new ExperimentRunner();

		t.experiment1();
	}

}
