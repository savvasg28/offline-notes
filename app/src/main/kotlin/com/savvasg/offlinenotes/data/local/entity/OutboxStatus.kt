package com.savvasg.offlinenotes.data.local.entity

import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp

enum class OutboxStatus { PENDING, IN_FLIGHT, PARKED }
