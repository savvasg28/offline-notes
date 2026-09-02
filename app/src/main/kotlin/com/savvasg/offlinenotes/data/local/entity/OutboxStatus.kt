package com.savvasg.offlinenotes.data.local.entity

import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp

/** Persistence-only. Never leaves the data layer — the sync engine sees [com.savvasg.offlinenotes.domain.model.FrozenOutboxOp] instead. */
enum class OutboxStatus { PENDING, IN_FLIGHT, PARKED }
