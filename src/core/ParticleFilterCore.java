package core;

import java.math.BigDecimal;
import java.util.*;
import java.util.Map.Entry;

import processing.core.*;

public class ParticleFilterCore extends PApplet {

	// Create default variables
	List<Landmark> landmarks = new ArrayList<>();
	final double WORLD_SIZE = 100.0;
	int maxParticles = 1000;
	Robot robot;
	Random rand = new Random();

	double FORWARD_NOISE = 0.05;
	double TURN_NOISE = 0.05;
	double SENSOR_NOISE = 5.0;

	// Collections to hold the particles and the readings generated by the robot
	List<Double> sensorReadings;
	List<Robot> particlesList;

	// user interface variables
	int Yellow = color(255, 255, 0);
	int Red = color(255, 0, 0);
	int Green = color(0, 255, 0);
	int Blue = color(0, 0, 255);
	int White = color(255);
	int Black = color(0);

	public void settings() {
		size(100, 100);
		noSmooth();
	}

	public void setup() {
		background(0);

		// setup vars
		landmarks.add(new Landmark(20.0, 20.0));
		landmarks.add(new Landmark(80.0, 80.0));
		landmarks.add(new Landmark(20.0, 80.0));
		landmarks.add(new Landmark(80.0, 20.0));

		// create robot - give map of world
		robot = new Robot(this, WORLD_SIZE, landmarks);

		particlesList = genParticles(maxParticles, WORLD_SIZE, landmarks, FORWARD_NOISE, TURN_NOISE, SENSOR_NOISE);
		// PApplet.println(particlesList.get(400));
		
		
		//for (int i = 0; i < 40; i++) {
			
			particlesList = moveParticles(particlesList, 0.1, 5);
			// PApplet.println(particlesList.get(400));

			sensorReadings = robot.sense();
			particlesList = weighParticles(particlesList, sensorReadings);
			// PApplet.println(particlesList.get(400).getWeight());

			//long startTime = System.currentTimeMillis();
			
			particlesList = resampleParticlesDouble(particlesList);
			
			//long endTime = System.currentTimeMillis();
		    //System.out.println("Total execution time: " + (endTime-startTime) + "ms\n\n"); 
			
			
		 //   printParticleDistributionCount(particlesList);
		 //   System.out.println("\n\n\n");
		//	}
		

	}

	public void draw() {
		
		background(0);
		robot = robot.move(0.1, 5);
		particlesList = moveParticles(particlesList, 0.1, 5);
		// PApplet.println(particlesList.get(400));

		sensorReadings = robot.sense();
		particlesList = weighParticles(particlesList, sensorReadings);
		// PApplet.println(particlesList.get(400).getWeight());

		//long startTime = System.currentTimeMillis();
		
		particlesList = resampleParticlesDouble(particlesList);
		
		strokeWeight(3);

		// set up the axis to begin at bottom left
		translate(0, height);
		scale(1, -1);

		// landmark points
		stroke(Green);
		for (Landmark currLandmark : landmarks) {
			point((float) currLandmark._xPos, (float) currLandmark._yPos);
		}

		
		
		// draw particles
		strokeWeight(1);
		stroke(Red);
		for (Robot par : particlesList) {
			point((float) par.getX(), (float) par.getY());
		}
		
		// draw robot
		strokeWeight(4);
		stroke(White);
		point((float) robot.getX(), (float) robot.getY());
		
		delay(100);
	}

	/**
	 * Create string representation of the distances to the landmarks
	 * 
	 * @param readings
	 *            the list of readings to turn into a string
	 * @return
	 */
	public String readingsToString(List<Double> readings) {

		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append('[');

		for (Iterator<Double> readingItr = readings.iterator(); readingItr.hasNext();) {

			double val = readingItr.next().doubleValue();
			stringBuilder.append(String.format("%.3f", val));

			if (readingItr.hasNext()) {
				stringBuilder.append(", ");
			} else {
				stringBuilder.append(']');
			}
		}
		return stringBuilder.toString();
	}

	/**
	 * Generate a random distribution of particles
	 * 
	 * @param numParticles
	 *            number of particles the distribution will have
	 * @param worldSize
	 *            the size of the world the particles will be placed in
	 * @param landmarks
	 *            landmarks that are in the world
	 * @return
	 */
	public List<Robot> genParticles(final int numParticles, double worldSize, List<Landmark> landmarks,
			double forwardNoise, double turnNoise, double sensorNoise) {

		List<Robot> genParticles = new ArrayList<>(numParticles);

		for (int i = 0; i < numParticles; i++) {
			Robot tempRobot = new Robot(this, worldSize, landmarks);
			tempRobot.setNoise(forwardNoise, turnNoise, sensorNoise);
			genParticles.add(tempRobot);
		}
		return genParticles;
	}

	/**
	 * Move a given distribution of particles by the provided parameters
	 * 
	 * @param originParticles
	 *            the set of particles to move
	 * @param turn
	 *            how much heading of each particle will change by
	 * @param forward
	 *            how much forward motion will each particle make
	 * @return a new distributions of particles that have been move using the
	 *         parameters
	 */
	public List<Robot> moveParticles(List<Robot> originParticles, double turn, double forward) {

		List<Robot> newParticles = new ArrayList<>(originParticles.size());

		for (Robot currParticle : originParticles) {
			newParticles.add(currParticle.move(turn, forward));
		}

		return newParticles;
	}

	public List<Robot> weighParticles(List<Robot> originParticles, List<Double> measurementVec) {

		List<Robot> weightedParticles = new ArrayList<>(originParticles.size());
		double weightSum = 0.0;

		// loop through each particle and calculate how plausible position is
		// given measurementVec
		for (Robot currParticle : originParticles) {

			double particleWeight = currParticle.measurementProb(measurementVec);
			weightSum += particleWeight;

			currParticle.setWeight(particleWeight);
			weightedParticles.add(currParticle);
		}

		// use the sum of the particle weights to give each particle a
		// normalised weight
		for (Robot currParticle : weightedParticles) {

			double normWeight = currParticle.getWeight() / weightSum;
			currParticle.setNormalisedWeight(normWeight);
		}

		return weightedParticles;
	}

	public List<Robot> resampleParticlesBigDecimal(List<Robot> originParticles) {

		// map the particles to a finite range less than 1 that defines
		// how likely they are to be correct, re-sample based on this map
		NavigableMap<BigDecimal, Robot> probMap = new TreeMap<>();
		final int ORIGIN_SIZE = originParticles.size();

		// accumulate the probability of each particle into probabilitySum and
		// use it to give each particle a slice of the values from 0 - 1
		BigDecimal probSum = new BigDecimal(0.0);
		for (Robot currParticle : originParticles) {

			// BigDecimal used for arbitrary precision arithmetic
			BigDecimal currentProb = new BigDecimal(currParticle.getNormalisedWeight());
			probSum = probSum.add(currentProb);
			probMap.put(probSum, currParticle);
		}

		// create a new empty list of particles to store the new samples
		List<Robot> newParticles = new ArrayList<>(ORIGIN_SIZE);

		// Loop through the probMap and randomly generate N keys to pick out
		// N new particles for the re-sample, the probability of a particle
		// being picked depends on the plausibility of the particles measurement
		// vector
		for (int i = 0; i < ORIGIN_SIZE; i++) {

			// use random num between [0 : 1) to choose particle
			BigDecimal randVal = new BigDecimal(rand.nextDouble());
			Robot pickedParticle = probMap.get(probMap.ceilingKey(randVal));

			// NOTE: we use the copy constructor here as each particle must be a
			// new distinct particle with its own allocated memory & attributes
			newParticles.add(new Robot(pickedParticle));
		}

		return newParticles;
	}
	
	public List<Robot> resampleParticlesDouble(List<Robot> originParticles) {

		// map the particles to a finite range less than 1 that defines
		// how likely they are to be correct, re-sample based on this map
		NavigableMap<Double, Robot> probMap = new TreeMap<>();
		final int ORIGIN_SIZE = originParticles.size();

		// accumulate the probability of each particle into probabilitySum and
		// use it to give each particle a slice of the values from 0 - 1
		Double probSum = 0.0;
		for (Robot currParticle : originParticles) {

			// BigDecimal used for arbitrary precision arithmetic
			Double currentProb = currParticle.getNormalisedWeight();
			probSum += currentProb;
			probMap.put(probSum, currParticle);
		}

		// create a new empty list of particles to store the new samples
		List<Robot> newParticles = new ArrayList<>(ORIGIN_SIZE);

		// Loop through the probMap and randomly generate N keys to pick out
		// N new particles for the re-sample, the probability of a particle
		// being picked depends on the plausibility of the particles measurement
		// vector
		for (int i = 0; i < ORIGIN_SIZE; i++) {

			// use random num between [0 : 1) to choose particle
			Double randVal = rand.nextDouble();
			Robot pickedParticle = probMap.get(probMap.ceilingKey(randVal));

			// NOTE: we use the copy constructor here as each particle must be a
			// new distinct particle with its own allocated memory & attributes
			newParticles.add(new Robot(pickedParticle));
		}

		return newParticles;
	}
	
	public void printParticleDistributionCount (List<Robot> particles) {
		
		// create a map that counts each particles occurrence in the re-sample
		NavigableMap<String, Integer> map = new TreeMap<>();
		for (Robot par : particles) {
			
			String posStr = String.format("X: %.3f Y: %.3f", par.getX(), par.getY());
			int val;
			if (map.containsKey(posStr)) {
				val = map.get(posStr);
				val++;
				map.put(posStr, val);
			}
			else {
				map.put(posStr, 1);
			}
		}
		
		int total = 0;
		for (Entry<String, Integer> entry : map.entrySet()) {
			String key = entry.getKey();
		    Integer value = entry.getValue();
		    total += value;
		    System.out.println("Pos: " + key + "   Count:" + value);
		}
		
		System.out.println("Total count:" + total);
	}

	/**
	 * Convert degrees to radians, (short hand wrapper method)
	 * 
	 * @param degrees
	 *            degrees value to convert
	 * @return converted value in radians
	 */
	public double d2r(double degrees) {
		return Math.toRadians(degrees);
	}

}
