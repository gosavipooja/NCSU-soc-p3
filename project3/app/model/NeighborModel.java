package model;

import java.util.Arrays;

public class NeighborModel {
	private String name;
	private double[] expertise;
	private double[] sociability;
	
	public NeighborModel() {
		
	}
	
	public NeighborModel(String name, double[] expertise, double[] sociability) {
		super();
		this.name = name;
		this.expertise = expertise;
		this.sociability = sociability;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public double[] getExpertise() {
		return expertise;
	}
	public void setExpertise(double[] expertise) {
		this.expertise = expertise;
	}
	public double[] getSociability() {
		return sociability;
	}
	public void setSociability(double[] sociability) {
		this.sociability = sociability;
	}

	@Override
	public String toString() {
		return "NeighborModel [name=" + name + ", expertise=" + Arrays.toString(expertise) + ", sociability="
				+ Arrays.toString(sociability) + "]";
	}
	
}
