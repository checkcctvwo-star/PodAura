package com.skyd.transcoder.di

import com.skyd.transcoder.Transcoder
import org.koin.dsl.module

val transcoderModule = module {
    single { Transcoder() }
}
