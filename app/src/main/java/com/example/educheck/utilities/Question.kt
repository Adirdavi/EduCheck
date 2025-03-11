package com.example.educheck.utilities

import android.os.Parcel
import android.os.Parcelable

data class Question(
    val id: String = "", // Unique ID for the question
    val text: String = "", // Question text
    val options: List<String> = listOf(), // Answer options
    val correctOptionIndex: Int = 0, // Index of the correct answer
    val points: Int = 10 // How many points the question is worth
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.createStringArrayList() ?: listOf(),
        parcel.readInt(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(text)
        parcel.writeStringList(options)
        parcel.writeInt(correctOptionIndex)
        parcel.writeInt(points)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Question> {
        override fun createFromParcel(parcel: Parcel): Question {
            return Question(parcel)
        }

        override fun newArray(size: Int): Array<Question?> {
            return arrayOfNulls(size)
        }
    }
}