package com.example.refresh_selection;


import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.datavec.api.records.Record;
import org.datavec.api.split.FileSplit;
import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.datasets.iterator.INDArrayDataSetIterator;
import org.deeplearning4j.datasets.iterator.impl.ListDataSetIterator;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.distribution.NormalDistribution;
import org.deeplearning4j.nn.conf.layers.CenterLossOutputLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.util.ModelSerializer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.ImagePreProcessingScaler;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.learning.config.NoOp;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Hashtable;
import java.io.FileReader;
import java.util.Random;
import java.util.UUID;

import kotlin.jvm.internal.Intrinsics;

import org.datavec.api.writable.Writable;

import java.util.List;
import java.util.Collection;
//import org.datavec.spark.functions.LineRecordReaderFunction;


public class DeepFM implements FederatedModel {
    private static final String TAG = "DeepFM";
    private static final int BATCH_SIZE = 50;
    private static final int labelIndex = 7;
    private static final int N_EPOCHS = 1;
    private static final int rngSeed = 42;

//    private static final int HEIGHT = 224;
//    private static final int WIDTH = 224;
//    private static final int OUTPUT_NUM = 5;

    private ComputationGraph model;

    private static Logger log = LoggerFactory.getLogger(DeepFM.class);

    private String train_data_path = "/storage/self/primary/Download/data_balance/client1_train/new_input.csv";
    private String test_data_path = "/storage/self/primary/Download/data_balance/test/";

//    private String mlsfc_vocab_path = "/storage/self/primary/Download/data_balance/client1_train/mlsfc_vocab.txt";
//    private String mcate_nm_path = "/storage/self/primary/Download/data_balance/client1_train/mcate_nm_vocab.txt";
//    private String day_path = "/storage/self/primary/Download/data_balance/client1_train/day_vocab.txt";
//    private String sex_path = "/storage/self/primary/Download/data_balance/client1_train/Sex_vocab.txt";
    private String mlsfc_vocab_path = "mlsfc_vocab.txt";
    private String mcate_nm_path = "mcate_nm_vocab.txt";
    private String day_path = "day_vocab.txt";
    private String sex_path = "Sex_vocab.txt";

    private DataSetIterator AcitivityTrain;
    private DataSetIterator AcitivityTest;
    private Context context;

    public DeepFM(Context context) throws IOException {
        this.context = context;
        AcitivityTrain = getDataSetIterator(train_data_path);
//        AcitivityTest = getTestDataSetIterator(test_data_path, N_SAMPLE_CLIENT_TEST);
    }

    public Hashtable get_vocab(String file_name) {
        Hashtable<String, Integer> vocab = null;
        try {
            vocab = new Hashtable<String, Integer>();

            AssetManager as = context.getResources().getAssets();
            InputStream is = as.open(file_name);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            String str;
            while (true) {
                if (!((str = reader.readLine()) != null))
                    break;
                else {
                    String[] key_val = str.split("\t");
                    vocab.put(key_val[0], Integer.parseInt(key_val[1]));
                }
            }
        } catch(IOException e){
            e.printStackTrace();
        }
        return vocab;
    }


    @Override
    public void buildModel(String modelzip_path) {

        //Load the model
        try {
            File modelzip = new File(modelzip_path + "/MyMultiLayerNetwork_beta6.zip");
            System.out.print(modelzip);
            model = ModelSerializer.restoreComputationGraph(modelzip);
            model.init();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void train(int numEpochs) throws InterruptedException {
        Log.d(TAG, " start fit!");
        model.fit(AcitivityTrain, numEpochs);
    }

    public String eval() {
        Evaluation model_eval = model.evaluate(AcitivityTrain);
        return Double.toString(model_eval.accuracy()) + "," + Double.toString(model_eval.f1());
    }

    @Override
    public void saveModel(String modelName) {
        try {
            File save_model = new File(modelName);
            model.save(save_model);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void saveSerializeModel(String modelName) {
        try {

            int layer_length = model.getNumLayers();
            JSONObject para_json = new JSONObject();
            for (int i = 0; i < layer_length; i++) {
                if (model.getLayer(i).getParam("W") != null) {
                    // 1. W param
                    JSONArray data_W = new JSONArray();
                    INDArray param_w = model.getLayer(i).getParam("W");
                    long[] param_shape_w = param_w.shape();

                    int total_size = 1;
                    for (int j = 0; j < param_shape_w.length; j++) {
                        total_size *= param_shape_w[j];
                    }
                    INDArray reshape_param = param_w.reshape(1, total_size);
                    for (int k = 0; k < reshape_param.getRow(0).length(); k++) {
                        data_W.put(reshape_param.getRow(0).getFloat(k));
                    }

                    // 2. b param
                    JSONArray data_b = new JSONArray();
                    INDArray param_b = model.getLayer(i).getParam("b");

                    for (int k = 0; k < param_b.columns(); k++) {
                        data_b.put(param_b.getRow(0).getFloat(k));
                    }

                    para_json.put(Integer.toString(i) + "_W", data_W);
                    para_json.put(Integer.toString(i) + "_b", data_b);
                }
            }

            FileWriter file = new FileWriter("/storage/self/primary/Download/save_weight/" + modelName);
            file.write(para_json.toString());
            file.flush();
            file.close();
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void uploadTo(String upload_path, String upload_url) throws IOException {

    }

    @Override
    public DataSetIterator getDataSetIterator(String filePath) throws IOException {

        // txt 파일 읽어서 리스트나 dictionary로 저자해놔야함
        Hashtable mlsfc_vocab_table = get_vocab(mlsfc_vocab_path);
        Hashtable mcate_nm_table = get_vocab(mcate_nm_path);
        Hashtable day_table = get_vocab(day_path);
        Hashtable sex_table = get_vocab(sex_path);


        //Load the training data:
        RecordReader rr = new CSVRecordReader(1);
        DataSetIterator dataIter = null;
        try {
            rr.initialize(new FileSplit(new File(filePath)));
            ArrayList originalData = new ArrayList();
            while (rr.hasNext()) {
                List line = rr.next();
                for (int i = 0; i < line.size(); i++) {
                    // encode
                    if(i == 3 || i == 4 || i ==5 || i == 8){
                        line.set(i, Integer.parseInt(line.get(i).toString()));
                    }else if( i == 0 ){
                        line.set(i, mlsfc_vocab_table.get(line.get(i).toString()));
                    }else if( i == 1 ){
                        line.set(i, mcate_nm_table.get(line.get(i).toString()));
                    }else if( i == 2){
                        line.set(i, sex_table.get(line.get(i).toString()));
                    }else if( i == 6 ){
                        line.set(i, day_table.get(line.get(i).toString()));
                    }else if( i == 7){
                        line.set(i, mcate_nm_table.get(line.get(i).toString()));
                    }
                }
                originalData.add(line);
            }

            int col_size = originalData.size();
            int row_size = ((ArrayList)(originalData.get(0))).size();

            DataSet dataSet = new DataSet();

            int[][] input_list = new int[col_size][row_size-1];
            int[][] output_list = new int[col_size][1];
            for(int i = 0; i<col_size; i++){
                for(int j = 0; j<row_size; j++){
                    if(j == row_size -1){
                        output_list[i][0] = Integer.parseInt(((ArrayList)originalData.get(i)).get(j).toString());
                    }else{
                        input_list[i][j] = Integer.parseInt(((ArrayList)originalData.get(i)).get(j).toString());
                    }
                }

            }

            INDArray input_ndarray = Nd4j.createFromArray(input_list);
            INDArray output_ndarray = Nd4j.createFromArray(output_list);
//            DataSet dataSet = new DataSet(input_ndarray, output_ndarray);
//            List<DataSet> listDs = dataSet.asList();

//            DataSet dataSet = new DataSet();
            dataSet.setFeatures(input_ndarray);
            dataSet.setLabels(output_ndarray);
            dataSet.dataSetBatches(4);
            List<DataSet> listDs = dataSet.asList();
//            System.out.print(fucku);

            dataIter = new ListDataSetIterator(listDs, 5);
            DataSet t = dataIter.next();
            System.out.print("Dataset : "+ t);
//            DataSet qq = dataIter.next();
//            System.out.print("hear ->" + dataIter.next());
            log.debug(dataIter.next().toString());
//            JavaRDD<List<Writable>> out = linesRdd.map(new LineRecordReaderFunction(rr));
//            List<List<Writable>> outList = out.collect();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return dataIter;
    }

    public void data_encode() {

//        LabelEncoder le = new LabelEncoder("module", "name");


    }
}
