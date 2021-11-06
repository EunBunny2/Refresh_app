package com.example.webapi;

import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.samediff.SameDiffLambdaLayer;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;

import java.util.UUID;

public class TensorsSquare extends SameDiffLambdaLayer {

    @Override
    public SDVariable defineLayer(SameDiff sameDiff, SDVariable layerInput) {
        return layerInput.mul("tensor_square-" + UUID.randomUUID().toString(),layerInput);
    }

    @Override
    public InputType getOutputType(int layerIndex, InputType inputType) {
        return inputType;
    }
}