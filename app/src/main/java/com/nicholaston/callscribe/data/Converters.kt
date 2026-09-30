package com.nicholaston.callscribe.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun callSource(value: CallSource): String = value.name
    @TypeConverter fun callSource(value: String): CallSource = enumValueOf(value)

    @TypeConverter fun callDirection(value: CallDirection): String = value.name
    @TypeConverter fun callDirection(value: String): CallDirection = enumValueOf(value)

    @TypeConverter fun channelLayout(value: ChannelLayout): String = value.name
    @TypeConverter fun channelLayout(value: String): ChannelLayout = enumValueOf(value)

    @TypeConverter fun callStatus(value: CallStatus): String = value.name
    @TypeConverter fun callStatus(value: String): CallStatus = enumValueOf(value)
}
