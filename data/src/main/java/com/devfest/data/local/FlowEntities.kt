package com.devfest.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flows")
data class FlowEntity(
    @PrimaryKey val id: String,
    val title: String,
    val graphJson: String,
    @ColumnInfo(name = "explanation") val explanation: String,
    @ColumnInfo(name = "risk_flags") val riskFlags: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = false,
    @ColumnInfo(name = "is_draft") val isDraft: Boolean = true
)
