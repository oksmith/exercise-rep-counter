// Copyright (c) 2024 Magic Tech Ltd

package fit.magic.cv.repcounter

import fit.magic.cv.PoseLandmarkerHelper
import kotlin.math.acos
import kotlin.math.sqrt

class ExerciseRepCounterImpl : ExerciseRepCounter() {

    // TODO: make things private?
    // fixed angles indicating full lunge stance and standing stance.
    // I didn't want to go for 90 degrees and 180 degrees as I wanted to allow some wiggle
    // room for differing technique, but this is something that could be adjusted.
    val minKneeAngle = 80.0
    val maxKneeAngle = 160.0

    var isLunging = false
    var progress = 0.0F

    fun calculateKneeAngle(
        hipX: Float, hipY: Float, hipZ: Float,
        kneeX: Float, kneeY: Float, kneeZ: Float,
        ankleX: Float, ankleY: Float, ankleZ: Float
    ): Float {
        // hip to knee vector
        val ax = hipX - kneeX
        val ay = hipY - kneeY
        val az = hipZ - kneeZ

        // knee to ankle vector
        val bx = kneeX - ankleX
        val by = kneeY - ankleY
        val bz = kneeZ - ankleZ

        // Calculate the angle in radians
        val dotProduct = ax * bx + ay * by + az * bz
        val magnitudeA = sqrt(ax * ax + ay * ay + az * az)
        val magnitudeB = sqrt(bx * bx + by * by + bz * bz)
        val angleRadians = acos(dotProduct / (magnitudeA * magnitudeB))

        // Convert the angle to degrees
        return Math.toDegrees(angleRadians.toDouble()).toFloat()
    }

    fun calculateKneeAngles(resultBundle: PoseLandmarkerHelper.ResultBundle): Pair<Float, Float> {

        // It seems like in the implementation of `returnLivestreamResult` it only returns the
        // pose estimation result of a single frame, wrapped up in a list. I'm not sure why this is
        // so I will just take the first entry of the list, but assert that it must be a list of
        // length zero just in case I'm missing something.
        assert(resultBundle.results.size == 1)
        val landmarks = resultBundle.results[0].landmarks()

        // when running this in debug mode, I noticed that again it is a list of length zero
        // I'm not sure why the data takes this form but I'll roll with it.
        // Might as well chuck in an assert so that I can find out if my theory is incorrect.
        assert(landmarks.size == 1)
        val leftHip = landmarks[0][23]
        val leftKnee = landmarks[0][25]
        val leftAnkle = landmarks[0][27]
        val rightHip = landmarks[0][24]
        val rightKnee = landmarks[0][26]
        val rightAnkle = landmarks[0][28]

        val leftAngle = calculateKneeAngle(
            leftHip.x(),
            leftHip.y(),
            leftHip.z(),
            leftKnee.x(),
            leftKnee.y(),
            leftKnee.z(),
            leftAnkle.x(),
            leftAnkle.y(),
            leftAnkle.z(),
        )
        val rightAngle = calculateKneeAngle(
            rightHip.x(),
            rightHip.y(),
            rightHip.z(),
            rightKnee.x(),
            rightKnee.y(),
            rightKnee.z(),
            rightAnkle.x(),
            rightAnkle.y(),
            rightAnkle.z(),
        )
        return Pair(leftAngle, rightAngle)
    }

    override fun setResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
//        0 - nose
//        1 - left eye (inner)
//        2 - left eye
//        3 - left eye (outer)
//        4 - right eye (inner)
//        5 - right eye
//        6 - right eye (outer)
//        7 - left ear
//        8 - right ear
//        9 - mouth (left)
//        10 - mouth (right)
//        11 - left shoulder
//        12 - right shoulder
//        13 - left elbow
//        14 - right elbow
//        15 - left wrist
//        16 - right wrist
//        17 - left pinky
//        18 - right pinky
//        19 - left index
//        20 - right index
//        21 - left thumb
//        22 - right thumb
//        23 - left hip
//        24 - right hip
//        25 - left knee
//        26 - right knee
//        27 - left ankle
//        28 - right ankle
//        29 - left heel
//        30 - right heel
//        31 - left foot index
//        32 - right foot index

        // I think we should use Landmarks (not WorldLandmarks) as they are normalised, which means
        // our algorithms to analyse reps should generalise nicely (assuming Google's normalisation
        // algorithm is decent -- I'm sure it is.)
        // If we used WorldLandmarks then our analysis is ultimately brittle because the the video
        // style might change every time

        // Let's start with a simple algorithm: Do the left hip and right hip (23 and 24) go up
        // and down sufficiently? Do the left knee and right knee (25 and 26) alternate going
        // forward and then back to neutral (z coordinate)?

        val (leftAngle, rightAngle) = calculateKneeAngles(resultBundle)
//        val (rightKneeDepth, leftKneeDepth) = getKneeDepth(resultBundle)

//        if (rightKneeDepth > leftKneeDepth) {
//          kneeAngle = rightAngle
//        } else {
//          kneeAngle = leftAngle
//        }

        if (kneeAngle <= minKneeAngle && !isLunging) {
            isLunging = true
            sendFeedbackMessage("You've reached the lunge position! Half way there!")
            progress = 0.5F
        } else if (kneeAngle > maxKneeAngle && isLunging) {
            isLunging = false
            sendFeedbackMessage("Rep completed!")
            incrementRepCount()
            progress = 0.0F
        }

        // Send progress update based on how deep the squat is (0.0 to 1.0 scale)
        // TODO: make it so that it's 0.5 at the bottom and 1.0 at the top. I.e. one full rep
        val squatProgress = (maxKneeAngle - kneeAngle) / (maxKneeAngle - minKneeAngle)
        sendProgressUpdate(squatProgress.coerceIn(0.0F, 1.0F))

    }
}
