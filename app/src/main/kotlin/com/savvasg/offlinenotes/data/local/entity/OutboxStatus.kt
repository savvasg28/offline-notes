package com.savvasg.offlinenotes.data.local.entity

import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp

/** Persistence-only: the sync engine sees [FrozenOutboxOp] instead. */
enum class OutboxStatus { PENDING, IN_FLIGHT, PARKED }
