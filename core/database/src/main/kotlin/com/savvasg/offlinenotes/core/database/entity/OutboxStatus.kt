package com.savvasg.offlinenotes.core.database.entity

import com.savvasg.offlinenotes.core.model.FrozenOutboxOp

enum class OutboxStatus { PENDING, IN_FLIGHT, PARKED }
