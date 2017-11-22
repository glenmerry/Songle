package com.example.glenmerry.songle

import android.os.Parcel
import android.os.Parcelable

data class Song(val number: String, val artist: String, val title: String, val link: String) : Parcelable {

    constructor(source: Parcel): this(source.readString(), source.readString(), source.readString(), source.readString())

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeString(this.number)
        dest?.writeString(this.artist)
        dest?.writeString(this.title)
        dest?.writeString(this.link)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Song> = object : Parcelable.Creator<Song> {
            override fun createFromParcel(source: Parcel): Song = Song(source)
            override fun newArray(size: Int): Array<Song?> = arrayOfNulls(size)
        }
    }
}