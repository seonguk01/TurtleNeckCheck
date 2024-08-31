package com.turtle.turtleneckcheckgit.common

class Constant {
    //서비스 액션
    interface ACTION {
        companion object {
            const val STARTFOREGROUND_ACTION = ActionIntent.STARTFOREGROUND_ACTION
            const val STOPFOREGROUND_ACTION = ActionIntent.STOPFOREGROUND_ACTION
        }
    }
    companion object{
        const val SERVICE_STATE = "SERVICE_STATE"
    }

}