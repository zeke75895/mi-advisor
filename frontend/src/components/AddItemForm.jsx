import { useState } from 'react'
import { courseApi } from '../api/client'
import { Button } from './Field'
import ItemForm from './ItemForm'

export default function AddItemForm({ courseId, onAdded }) {
  const [open, setOpen] = useState(false)
  if (!open) {
    return (
      <Button variant="secondary" onClick={() => setOpen(true)} className="w-full">
        + Add graded item
      </Button>
    )
  }
  return (
    <ItemForm
      submitLabel="Add item"
      busyLabel="Adding…"
      onCancel={() => setOpen(false)}
      onSubmit={async (item) => {
        await courseApi.addItem(courseId, item)
        setOpen(false)
        onAdded()
      }}
    />
  )
}
