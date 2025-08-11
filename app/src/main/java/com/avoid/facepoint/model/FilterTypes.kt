package com.avoid.facepoint.model

enum class FilterTypes(val faceMesh: Boolean) {
    DEFAULT(false),
    LUT(false),
    INVERSE(false),
    BULGE(true),
    BULGE_DOUBLE(true),
    GLASSES(true),
    EYE_MOUTH(true),
    EYE_RECT(true),
//    FILTER3D{}
}